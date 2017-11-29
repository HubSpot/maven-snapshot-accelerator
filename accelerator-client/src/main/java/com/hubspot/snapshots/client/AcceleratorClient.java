package com.hubspot.snapshots.client;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.snapshots.core.SnapshotVersion;
import com.hubspot.snapshots.core.SnapshotVersionEgg;
import com.hubspot.snapshots.core.Snapshots;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AcceleratorClient {
  private static final String DETECTED_BASE_URL = detectBaseUrl();

  private static String detectBaseUrl() {
    String acceleratorUrl = System.getProperty("accelerator.url");
    if (acceleratorUrl != null) {
      return acceleratorUrl;
    }

    return System.getenv("ACCELERATOR_URL");
  }

  private final String reportUrl;
  private final String deltaUrl;
  private final OkHttpClient client;
  private final ObjectMapper mapper;

  private AcceleratorClient(String baseUrl) {
    this.reportUrl = baseUrl + "/snapshots";
    this.deltaUrl = baseUrl + "/snapshots/delta";
    this.client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    this.mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public static AcceleratorClient detectingBaseUrl() {
    if (DETECTED_BASE_URL == null) {
      throw new IllegalStateException("Unable to detect base url, set ACCELERATOR_URL environment variable or accelerator.url system property");
    }
    return withBaseUrl(DETECTED_BASE_URL);
  }

  public static AcceleratorClient withBaseUrl(String baseUrl) {
    return new AcceleratorClient(baseUrl);
  }

  public static String detectedDeltaUrl() {
    return DETECTED_BASE_URL + "/snapshots/delta";
  }

  public Iterator<SnapshotVersion> getDelta(int offset) {
    return new SnapshotIterator(offset);
  }

  public SnapshotVersion report(SnapshotVersionEgg snapshot) throws IOException {
    MediaType mediaTye = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(mediaTye, mapper.writeValueAsString(snapshot));

    Request request = new Request.Builder()
            .url(reportUrl)
            .post(body)
            .build();

    Response response = client.newCall(request).execute();
    if (response.code() != 200) {
      throw new IOException("Unexpected response code from accelerator API: " + response.code());
    }

    return mapper.readValue(response.body().byteStream(), SnapshotVersion.class);
  }

  private Snapshots getSinglePage(int offset) throws IOException {
    Request request = new Request.Builder()
            .url(deltaUrl + "?offset=" + offset)
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (response.code() != 200) {
        throw new IOException("Unexpected response code from accelerator API: " + response.code());
      }

      return mapper.readValue(response.body().byteStream(), Snapshots.class);
    }
  }

  private enum State {
    READY, NOT_READY, DONE, FAILED
  }

  private class SnapshotIterator implements Iterator<SnapshotVersion> {
    private final int initialOffset;
    private State state;
    private SnapshotVersion next;
    private Snapshots snapshots;
    private Iterator<SnapshotVersion> iterator;

    public SnapshotIterator(int initialOffset) {
      this.initialOffset = initialOffset;
      this.state = State.NOT_READY;
      this.next = null;
      this.snapshots = null;
      this.iterator = null;
    }

    @Override
    public boolean hasNext() {
      if (state == State.FAILED) {
        throw new IllegalStateException("This iterator is in a failed state");
      }
      switch (state) {
        case DONE:
          return false;
        case READY:
          return true;
        default:
          return tryToComputeNext();
      }
    }

    @Override
    public SnapshotVersion next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      state = State.NOT_READY;
      SnapshotVersion snapshot = next;
      next = null;
      return snapshot;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }

    private boolean tryToComputeNext() {
      state = State.FAILED; // temporary pessimism
      next = computeNext();
      if (state != State.DONE) {
        state = State.READY;
        return true;
      }
      return false;
    }

    private SnapshotVersion computeNext() {
      try {
        if (snapshots == null) {
          snapshots = getSinglePage(initialOffset);
          iterator = snapshots.getVersions().iterator();
        }

        while (!iterator.hasNext()) {
          if (snapshots.hasMore()) {
            snapshots = getSinglePage(snapshots.getNextOffset());
            iterator = snapshots.getVersions().iterator();
          } else {
            state = State.DONE;
            return null;
          }
        }

        return iterator.next();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
