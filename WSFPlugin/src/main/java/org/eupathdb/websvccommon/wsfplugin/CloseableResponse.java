package org.eupathdb.websvccommon.wsfplugin;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Link.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

public class CloseableResponse extends Response implements AutoCloseable {

  private final Response _underlyingResponse;


  public CloseableResponse(Response underlyingResponse) {
    _underlyingResponse = underlyingResponse;
  }

  @Override
  public int hashCode() {
    return _underlyingResponse.hashCode();
  }

  @Override
  public int getStatus() {
    return _underlyingResponse.getStatus();
  }

  @Override
  public StatusType getStatusInfo() {
    return _underlyingResponse.getStatusInfo();
  }

  @Override
  public boolean equals(Object obj) {
    return _underlyingResponse.equals(obj);
  }

  @Override
  public Object getEntity() {
    return _underlyingResponse.getEntity();
  }

  @Override
  public <T> T readEntity(Class<T> entityType) {
    return _underlyingResponse.readEntity(entityType);
  }

  @Override
  public <T> T readEntity(GenericType<T> entityType) {
    return _underlyingResponse.readEntity(entityType);
  }

  @Override
  public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
    return _underlyingResponse.readEntity(entityType, annotations);
  }

  @Override
  public String toString() {
    return _underlyingResponse.toString();
  }

  @Override
  public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
    return _underlyingResponse.readEntity(entityType, annotations);
  }

  @Override
  public boolean hasEntity() {
    return _underlyingResponse.hasEntity();
  }

  @Override
  public boolean bufferEntity() {
    return _underlyingResponse.bufferEntity();
  }

  @Override
  public void close() {
    _underlyingResponse.close();
  }

  @Override
  public MediaType getMediaType() {
    return _underlyingResponse.getMediaType();
  }

  @Override
  public Locale getLanguage() {
    return _underlyingResponse.getLanguage();
  }

  @Override
  public int getLength() {
    return _underlyingResponse.getLength();
  }

  @Override
  public Set<String> getAllowedMethods() {
    return _underlyingResponse.getAllowedMethods();
  }

  @Override
  public Map<String, NewCookie> getCookies() {
    return _underlyingResponse.getCookies();
  }

  @Override
  public EntityTag getEntityTag() {
    return _underlyingResponse.getEntityTag();
  }

  @Override
  public Date getDate() {
    return _underlyingResponse.getDate();
  }

  @Override
  public Date getLastModified() {
    return _underlyingResponse.getLastModified();
  }

  @Override
  public URI getLocation() {
    return _underlyingResponse.getLocation();
  }

  @Override
  public Set<Link> getLinks() {
    return _underlyingResponse.getLinks();
  }

  @Override
  public boolean hasLink(String relation) {
    return _underlyingResponse.hasLink(relation);
  }

  @Override
  public Link getLink(String relation) {
    return _underlyingResponse.getLink(relation);
  }

  @Override
  public Builder getLinkBuilder(String relation) {
    return _underlyingResponse.getLinkBuilder(relation);
  }

  @Override
  public MultivaluedMap<String, Object> getMetadata() {
    return _underlyingResponse.getMetadata();
  }

  @Override
  public MultivaluedMap<String, Object> getHeaders() {
    return _underlyingResponse.getHeaders();
  }

  @Override
  public MultivaluedMap<String, String> getStringHeaders() {
    return _underlyingResponse.getStringHeaders();
  }

  @Override
  public String getHeaderString(String name) {
    return _underlyingResponse.getHeaderString(name);
  }
}