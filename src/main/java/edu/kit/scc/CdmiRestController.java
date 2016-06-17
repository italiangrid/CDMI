/*
 * Copyright 2016 Karlsruhe Institute of Technology (KIT)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.kit.scc;

import edu.kit.scc.http.HttpClient;
import edu.kit.scc.http.HttpResponse;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snia.cdmiserver.dao.CapabilityDao;
import org.snia.cdmiserver.dao.CdmiObjectDao;
import org.snia.cdmiserver.dao.ContainerDao;
import org.snia.cdmiserver.dao.DataObjectDao;
import org.snia.cdmiserver.exception.BadRequestException;
import org.snia.cdmiserver.model.Capability;
import org.snia.cdmiserver.model.CdmiObject;
import org.snia.cdmiserver.model.Container;
import org.snia.cdmiserver.model.DataObject;
import org.snia.cdmiserver.model.Domain;
import org.snia.cdmiserver.util.MediaTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

@RestController
@ComponentScan(basePackages = {"edu.kit.scc", "org.snia.cdmiserver"})
public class CdmiRestController {

  private static final Logger log = LoggerFactory.getLogger(CdmiRestController.class);

  @Autowired
  private CdmiObjectDao cdmiObjectDao;

  @Autowired
  private CapabilityDao capabilityDao;

  @Autowired
  private ContainerDao containerDao;

  @Autowired
  private DataObjectDao dataObjectDao;

  @Autowired
  private HttpClient httpClient;

  @Value("${rest.user}")
  private String restUser;

  @Value("${rest.pass}")
  private String restPassword;

  @Value("${oidc.tokeninfo}")
  private String tokenInfo;

  @Value("${oidc.userinfo}")
  private String userInfo;

  @Value("${oidc.clientid}")
  private String clientId;

  @Value("${oidc.clientsecret}")
  private String clientSecret;

  /**
   * Domains endpoint.
   * 
   * @param request the {@link HttpServletRequest}
   * @return a JSON serialized {@link Domain} object
   */
  @RequestMapping(path = "/cdmi_domains/**", method = RequestMethod.GET,
      produces = {"application/cdmi-domain"})
  public ResponseEntity<?> getDomains(@RequestHeader("Authorization") String authorizationHeader,
      HttpServletRequest request) {

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.add("X-CDMI-Specification-Version", "1.1.1");
    responseHeaders.setContentType(new MediaType("application", "cdmi-domain"));

    if (!verifyAuthorization(authorizationHeader)) {
      return new ResponseEntity<>(responseHeaders, HttpStatus.UNAUTHORIZED);
    }

    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

    log.debug("Requested domain path {}", path);

    // TODO implement get domains

    return new ResponseEntity<String>("Domain not found", responseHeaders, HttpStatus.NOT_FOUND);
  }

  /**
   * Capabilities endpoint.
   * 
   * @param request the {@link HttpServletRequest}
   * @return a JSON serialized {@link Capability} object
   */
  @RequestMapping(path = "/cdmi_capabilities/**", method = RequestMethod.GET,
      produces = {"application/cdmi-capability"})
  public ResponseEntity<?> getCapabilities(
      @RequestHeader("Authorization") String authorizationHeader, HttpServletRequest request) {

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(new MediaType("application", "cdmi-capability"));
    responseHeaders.add("X-CDMI-Specification-Version", "1.1.1");

    if (!verifyAuthorization(authorizationHeader)) {
      return new ResponseEntity<>(responseHeaders, HttpStatus.UNAUTHORIZED);
    }

    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    log.debug("Requested capabilities path {}", path);

    Capability capability = capabilityDao.findByPath(path);

    if (capability != null) {
      return new ResponseEntity<String>(capability.toJson().toString(), responseHeaders,
          HttpStatus.OK);
    }

    return new ResponseEntity<String>("Capabilities not found", responseHeaders,
        HttpStatus.NOT_FOUND);
  }

  /**
   * ObjectId endpoint.
   * 
   * @param request the {@link HttpServletRequest}
   * @return a JSON serialized {@link CdmiObject}
   */
  @RequestMapping(path = "/cdmi_objectid/{objectId}", method = RequestMethod.GET,
      produces = {"application/cdmi-object", "application/cdmi-container",
          "application/cdmi-domain", "application/cdmi-capability"})
  public ResponseEntity<?> getCdmiObjectById(
      @RequestHeader("Authorization") String authorizationHeader, @PathVariable String objectId,
      HttpServletRequest request) {

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.add("X-CDMI-Specification-Version", "1.1.1");
    responseHeaders.setContentType(new MediaType("application", "cdmi-object"));

    if (!verifyAuthorization(authorizationHeader)) {
      return new ResponseEntity<>(responseHeaders, HttpStatus.UNAUTHORIZED);
    }

    log.debug("Get objectID {}", objectId);

    CdmiObject cdmiObject = cdmiObjectDao.getCdmiObject(objectId);

    if (cdmiObject != null) {
      if (cdmiObject instanceof Container) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-container"));
        Container container = (Container) cdmiObject;
        return new ResponseEntity<String>(container.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      } else if (cdmiObject instanceof DataObject) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-object"));
        DataObject dataObject = (DataObject) cdmiObject;
        return new ResponseEntity<String>(dataObject.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      } else if (cdmiObject instanceof Capability) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-capability"));
        Capability capability = (Capability) cdmiObject;
        return new ResponseEntity<String>(capability.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      } else if (cdmiObject instanceof Domain) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-domain"));
        Domain domain = (Domain) cdmiObject;
        return new ResponseEntity<String>(domain.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      }
    }
    return new ResponseEntity<String>("Object not found", responseHeaders, HttpStatus.NOT_FOUND);
  }

  /**
   * Get path endpoint.
   * 
   * @param request the {@link HttpServletRequest}
   * @return a JSON serialized {@link Container} or {@link DataObject}
   */
  @RequestMapping(path = "/**", method = RequestMethod.GET,
      produces = {"application/cdmi-object", "application/cdmi-container"})
  public ResponseEntity<?> getCdmiObjectByPath(
      @RequestHeader("Authorization") String authorizationHeader, HttpServletRequest request) {

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.add("X-CDMI-Specification-Version", "1.1.1");
    responseHeaders.setContentType(new MediaType("application", "cdmi-object"));

    if (!verifyAuthorization(authorizationHeader)) {
      return new ResponseEntity<>(responseHeaders, HttpStatus.UNAUTHORIZED);
    }

    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

    log.debug("Get path {}", path);

    CdmiObject cdmiObject = cdmiObjectDao.getCdmiObjectByPath(path);

    if (cdmiObject != null) {
      if (cdmiObject instanceof Container) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-container"));
        Container container = (Container) cdmiObject;
        return new ResponseEntity<String>(container.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      } else if (cdmiObject instanceof DataObject) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-object"));
        DataObject dataObject = (DataObject) cdmiObject;
        return new ResponseEntity<String>(dataObject.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      } else if (cdmiObject instanceof Capability) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-capability"));
        Capability capability = (Capability) cdmiObject;
        return new ResponseEntity<String>(capability.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      } else if (cdmiObject instanceof Domain) {
        responseHeaders.setContentType(new MediaType("application", "cdmi-domain"));
        Domain domain = (Domain) cdmiObject;
        return new ResponseEntity<String>(domain.toJson().toString(), responseHeaders,
            HttpStatus.OK);
      }
    }
    return new ResponseEntity<String>("Object not found", responseHeaders, HttpStatus.NOT_FOUND);
  }

  /**
   * Put path endpoint.
   * 
   * @param request the {@link HttpServletRequest}
   * @return a JSON serialized {@link Container} or {@link DataObject}
   */
  @RequestMapping(path = "/**", method = RequestMethod.PUT,
      consumes = {"application/cdmi-object", "application/cdmi-container", "application/json"},
      produces = {"application/cdmi-object", "application/cdmi-container"})
  public ResponseEntity<?> putCdmiObject(@RequestHeader("Authorization") String authorizationHeader,
      @RequestHeader("Content-Type") String contentType, @RequestBody String body,
      HttpServletRequest request) {

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.add("X-CDMI-Specification-Version", "1.1.1");
    responseHeaders.setContentType(new MediaType("application", "cdmi-object"));

    if (!verifyAuthorization(authorizationHeader)) {
      return new ResponseEntity<>(responseHeaders, HttpStatus.UNAUTHORIZED);
    }

    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

    log.debug("Create path {} as {}", path, contentType);

    if (contentType.contains(MediaTypes.CONTAINER)) {
      log.debug("Create container...");
      Container containerRequest = Container.fromJson(new JSONObject(body));

      Container createdContainer = containerDao.createByPath(path, containerRequest);

      if (createdContainer != null) {
        return new ResponseEntity<String>(createdContainer.toJson().toString(), responseHeaders,
            HttpStatus.CREATED);
      } else {
        return new ResponseEntity<String>("Container could not be created", responseHeaders,
            HttpStatus.CONFLICT);
      }
    }
    if (contentType.contains(MediaTypes.DATA_OBJECT)) {
      log.debug("Create data object...");
      DataObject dataObjectRequest = DataObject.fromJson(new JSONObject(body));

      DataObject createdObject = dataObjectDao.createByPath(path, dataObjectRequest);

      if (createdObject != null) {
        return new ResponseEntity<String>(createdObject.toJson().toString(), responseHeaders,
            HttpStatus.CREATED);
      } else {
        return new ResponseEntity<String>("Container could not be created", responseHeaders,
            HttpStatus.CONFLICT);
      }
    }
    return new ResponseEntity<String>("Bad request", responseHeaders, HttpStatus.BAD_REQUEST);
  }

  /**
   * Delete path endpoint.
   * 
   * @param request the {@link HttpServletRequest}
   * @return a {@link ResponseEntity}
   */
  @RequestMapping(path = "/**", method = RequestMethod.DELETE)
  public ResponseEntity<?> deleteCdmiObject(
      @RequestHeader("Authorization") String authorizationHeader, HttpServletRequest request) {

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.add("X-CDMI-Specification-Version", "1.1.1");

    if (!verifyAuthorization(authorizationHeader)) {
      return new ResponseEntity<>(responseHeaders, HttpStatus.UNAUTHORIZED);
    }

    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

    log.debug("Delete path {}", path);

    CdmiObject cdmiObject = cdmiObjectDao.getCdmiObjectByPath(path);

    if (cdmiObject != null) {
      if (cdmiObject instanceof Container) {
        Container container = containerDao.deleteByPath(path);
        if (container != null) {
          return new ResponseEntity<String>("Container deleted", responseHeaders,
              HttpStatus.NO_CONTENT);
        } else {
          return new ResponseEntity<String>("Container could not be deleted", responseHeaders,
              HttpStatus.CONFLICT);
        }
      } else if (cdmiObject instanceof DataObject) {
        DataObject dataObject = dataObjectDao.deleteByPath(path);
        if (dataObject != null) {
          return new ResponseEntity<String>("Data object deleted", responseHeaders,
              HttpStatus.NO_CONTENT);
        } else {
          return new ResponseEntity<String>("Data object could not be deleted", responseHeaders,
              HttpStatus.CONFLICT);
        }
      }
    }
    return new ResponseEntity<String>("Not found", responseHeaders, HttpStatus.NOT_FOUND);
  }

  @SuppressWarnings("unused")
  private String[] parseFields(HttpServletRequest request) {
    Enumeration<String> attributes = request.getParameterNames();
    String[] requestedFields = null;
    while (attributes.hasMoreElements()) {
      String attributeName = attributes.nextElement();
      requestedFields = attributeName.split(";");
    }
    return requestedFields;
  }

  @SuppressWarnings("unused")
  private JSONObject getRequestedJson(JSONObject object, String[] requestedFields) {
    JSONObject requestedJson = new JSONObject();
    try {
      for (int i = 0; i < requestedFields.length; i++) {
        String field = requestedFields[i];
        if (!field.contains(":")) {
          requestedJson.put(field, object.get(field));
        } else {
          String[] fieldsplit = field.split(":");
          if (object.get(fieldsplit[0]) instanceof JSONObject) {
            JSONObject fieldObject = new JSONObject();
            String prefix = fieldsplit[1];
            String fieldname = fieldsplit[0];
            if (requestedJson.has(fieldname)) {
              fieldObject = requestedJson.getJSONObject(fieldname);
            }
            Iterator<?> keys = object.getJSONObject(fieldname).keys();
            while (keys.hasNext()) {
              String key = (String) keys.next();
              if (key.startsWith(prefix)) {
                fieldObject.put(key, object.getJSONObject(fieldname).get(key));
              }
            }
            if (fieldObject.length() != 0) {
              requestedJson.put(fieldname, fieldObject);
            }
          } else if (field.startsWith("children:")) {
            String range = field.split("children:")[1];
            String[] rangeSplit = range.split("-");
            List<String> requestedChildren = new ArrayList<String>();
            JSONArray children = object.getJSONArray("children");
            int startIndex = Integer.valueOf(rangeSplit[0]);
            if (rangeSplit.length > 1) {
              int endIndex = Integer.valueOf(rangeSplit[1]);
              for (int j = startIndex; j <= endIndex; j++) {
                requestedChildren.add(children.getString(j));
              }
            } else {
              requestedChildren.add(children.getString(startIndex));
            }
            requestedJson.put("children", requestedChildren);
          } else if (field.startsWith("value:")) {
            String range = field.split("value:")[1];
            String[] rangeSplit = range.split("-");
            requestedJson.put("value",
                new String(Arrays.copyOfRange(object.getString("value").getBytes(),
                    Integer.valueOf(rangeSplit[0].trim()), Integer.valueOf(rangeSplit[1].trim()))));
          } else {
            throw new BadRequestException("Bad prefix");
          }

        }
      }
      if (requestedJson.has("childrenrange") && requestedJson.has("children")) {
        requestedJson.put("childrenrange",
            "0-" + String.valueOf(requestedJson.getJSONArray("children").length() - 1));
      }
    } catch (JSONException e) {
      throw new BadRequestException("bad field");
    } catch (IndexOutOfBoundsException | NumberFormatException e) {
      throw new BadRequestException("bad range");
    }
    return requestedJson;
  }

  /**
   * Verifies the authorization according to the authorization header.
   * 
   * @param authorizationHeader the authorization header
   * @return true if authorized
   */
  public boolean verifyAuthorization(String authorizationHeader) {
    try {
      log.debug("Authorization: {}", authorizationHeader);
      String authorizationMethod = authorizationHeader.split(" ")[0];
      String encodedCredentials = authorizationHeader.split(" ")[1];

      if (authorizationMethod.equals("Basic")) {
        String[] credentials = new String(Base64.decodeBase64(encodedCredentials)).split(":");

        if (credentials[0].equals(restUser) && credentials[1].equals(restPassword)) {
          return true;
        }
        log.error("Wrong credentials {} {}", credentials[0], credentials[1]);
      } else if (authorizationMethod.equals("Bearer")) {
        // check for user token
        HttpResponse response = httpClient.makeHttpsGetRequest(encodedCredentials, userInfo);
        if (response != null && response.statusCode == HttpStatus.OK.value()) {
          // TODO set user ACLs
          return true;
        }
        // check for client token
        String body = "token=" + encodedCredentials;
        response = httpClient.makeHttpsPostRequest(clientId, clientSecret, body, tokenInfo);
        if (response.statusCode == HttpStatus.OK.value()) {
          // TODO set client ACLs
          return true;
        }
      }
    } catch (Exception ex) {
      log.error("ERROR {}", ex.toString());
      // ex.printStackTrace();
    }
    return false;
  }

}
