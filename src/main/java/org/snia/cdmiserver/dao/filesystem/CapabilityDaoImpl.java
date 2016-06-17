/*
 * Original work Copyright (c) 2010, Sun Microsystems, Inc. Copyright (c) 2010, The Storage
 * Networking Industry Association.
 *
 * Modified work Copyright (c) 2016, Karlsruhe Institute of Technology (KIT)
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of The Storage Networking Industry Association (SNIA) nor the names of its
 * contributors may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.snia.cdmiserver.dao.filesystem;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snia.cdmiserver.dao.CapabilityDao;
import org.snia.cdmiserver.dao.CdmiObjectDao;
import org.snia.cdmiserver.model.Capability;
import org.snia.cdmiserver.model.CdmiObject;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * <p>
 * Concrete implementation of {@link CapabilityObjectDao} using the local filesystem as the backing
 * store.
 * </p>
 */
public class CapabilityDaoImpl implements CapabilityDao {

  private static final Logger LOG = LoggerFactory.getLogger(CapabilityDaoImpl.class);

  private String baseDirectory;

  private CdmiObjectDao cdmiObjectDao;

  public String getBaseDirectory() {
    return baseDirectory;
  }

  public void setBaseDirectory(String baseDirectory) {
    this.baseDirectory = baseDirectory;
  }

  public CdmiObjectDao getCdmiObjectDao() {
    return cdmiObjectDao;
  }

  public void setCdmiObjectDao(CdmiObjectDao cdmiObjectDao) {
    this.cdmiObjectDao = cdmiObjectDao;
  }

  @Override
  public Capability findByObjectId(String objectId) {
    return (Capability) cdmiObjectDao.getCdmiObject(objectId);
  }

  @Override
  public Capability findByPath(String path) {
    return (Capability) cdmiObjectDao
        .getCdmiObjectByPath(Paths.get(baseDirectory.trim(), path.trim()).toString());
  }

  @Override
  public Capability createByPath(String path, Capability capabilityRequest) {
    if (path == null) {
      return null;
    }

    // TODO: only allow paths containing "cdmi_capabilities"

    final Path capabilityPath = Paths.get(baseDirectory.trim(), path.trim());

    try {
      Files.createDirectory(capabilityPath);
      LOG.debug("create capability object {}", path.trim());
    } catch (FileAlreadyExistsException ex) {
      LOG.error(ex.getMessage());
      return null;
    } catch (Exception ex) {
      LOG.error(ex.getMessage());
      return null;
    }

    String objectName = capabilityPath.getFileName().toString();
    String parentUri = Paths.get(path.trim()).getParent().toString();
    String parentPath = capabilityPath.getParent().toString();
    Capability parentCapability =
        (Capability) cdmiObjectDao.getCdmiObjectByPath(parentPath.toString());

    Capability capability = new Capability(objectName, parentUri, parentCapability.getObjectId());
    if (parentCapability.getChildren() == null) {
      parentCapability.setChildren(new JSONArray());
    }
    parentCapability.getChildren().put(capability.getObjectName());
    String childrenRange = CdmiObject.getChildrenRange(parentCapability.getChildren());
    parentCapability.setChildrenrange(childrenRange);

    capability.setMetadata(capabilityRequest.getMetadata());
    capability.setCapabilities(capabilityRequest.getCapabilities());

    cdmiObjectDao.createCdmiObject(capability, capabilityPath.toString());
    cdmiObjectDao.updateCdmiObject(parentCapability);

    return capability;
  }

}
