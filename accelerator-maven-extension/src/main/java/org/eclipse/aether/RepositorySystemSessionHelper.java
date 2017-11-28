package org.eclipse.aether;

public class RepositorySystemSessionHelper {

  public static RepositorySystemSession getSession(AbstractForwardingRepositorySystemSession session) {
    return session.getSession();
  }
}
