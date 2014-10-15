/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package monasca.api.resource;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hibernate.validator.constraints.NotEmpty;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Strings;
import monasca.api.app.AlarmDefinitionService;
import monasca.api.app.command.CreateAlarmDefinitionCommand;
import monasca.api.app.command.UpdateAlarmDefinitionCommand;
import monasca.api.app.validation.AlarmValidation;
import monasca.api.app.validation.Validation;
import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmState;
import monasca.api.domain.model.alarmdefinition.AlarmDefinition;
import monasca.api.domain.model.alarmdefinition.AlarmDefinitionRepository;
import monasca.api.resource.annotation.PATCH;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * Alarm definition resource implementation.
 */
@Path(AlarmDefinitionResource.ALARM_DEFINITIONS_PATH)
@Api(value = AlarmDefinitionResource.ALARM_DEFINITIONS_PATH,
    description = "Operations for working with alarm definitions")
public class AlarmDefinitionResource {
  private final AlarmDefinitionService service;
  private final AlarmDefinitionRepository repo;
  public final static String ALARM_DEFINITIONS = "alarm-definitions";
  public final static String ALARM_DEFINITIONS_PATH = "/v2.0/" + ALARM_DEFINITIONS;

  @Inject
  public AlarmDefinitionResource(AlarmDefinitionService service, AlarmDefinitionRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  @POST
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create alarm definition", response = AlarmDefinition.class)
  public Response create(@Context UriInfo uriInfo, @HeaderParam("X-Tenant-Id") String tenantId,
      @Valid CreateAlarmDefinitionCommand command) {
    command.validate();
    AlarmExpression alarmExpression = AlarmValidation.validateNormalizeAndGet(command.expression);
    AlarmDefinition alarm =
        Links.hydrate(service.create(tenantId, command.name, command.description, command.severity,
            command.expression, alarmExpression, command.matchBy, command.alarmActions,
            command.okActions, command.undeterminedActions), uriInfo, false);
    return Response.created(URI.create(alarm.getId())).entity(alarm).build();
  }

  @GET
  @Timed
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "List alarm definitions", response = AlarmDefinition.class,
      responseContainer = "List")
  public List<AlarmDefinition> list(@Context UriInfo uriInfo,
      @HeaderParam("X-Tenant-Id") String tenantId, @QueryParam("name") String name,
      @QueryParam("dimensions") String dimensionsStr) {
    Map<String, String> dimensions =
        Strings.isNullOrEmpty(dimensionsStr) ? null : Validation
            .parseAndValidateDimensions(dimensionsStr);
    return Links.hydrate(repo.find(tenantId, name, dimensions), uriInfo);
  }

  @GET
  @Timed
  @Path("/{alarm_definition_id}")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get alarm definition", response = AlarmDefinition.class)
  @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid ID supplied"),
      @ApiResponse(code = 404, message = "Alarm definition not found")})
  public AlarmDefinition get(
      @ApiParam(value = "ID of alarm definition to fetch", required = true) @Context UriInfo uriInfo,
      @HeaderParam("X-Tenant-Id") String tenantId,
      @PathParam("alarm_definition_id") String alarmDefinitionId) {
    return Links.hydrate(repo.findById(tenantId, alarmDefinitionId), uriInfo, true);
  }

  @PUT
  @Timed
  @Path("/{alarm_definition_id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update alarm definition", response = AlarmDefinition.class)
  public AlarmDefinition update(@Context UriInfo uriInfo,
      @HeaderParam("X-Tenant-Id") String tenantId,
      @PathParam("alarm_definition_id") String alarmDefinitionId,
      @Valid UpdateAlarmDefinitionCommand command) {
    command.validate();
    AlarmExpression alarmExpression = AlarmValidation.validateNormalizeAndGet(command.expression);
    return Links.hydrate(service.update(tenantId, alarmDefinitionId, alarmExpression, command),
        uriInfo, true);
  }

  @PATCH
  @Timed
  @Path("/{alarm_definition_id}")
  @Consumes("application/json-patch+json")
  @Produces(MediaType.APPLICATION_JSON)
  @SuppressWarnings("unchecked")
  public AlarmDefinition patch(@Context UriInfo uriInfo,
      @HeaderParam("X-Tenant-Id") String tenantId,
      @PathParam("alarm_definition_id") String alarmDefinitionId,
      @NotEmpty Map<String, Object> fields) throws JsonMappingException {
    String name = (String) fields.get("name");
    String description = (String) fields.get("description");
    String severity = (String) fields.get("severity");
    String expression = (String) fields.get("expression");
    List<String> matchBy = (List<String>) fields.get("match_by");
    String stateStr = (String) fields.get("state");
    AlarmState state =
        stateStr == null ? null : Validation.parseAndValidate(AlarmState.class, stateStr);
    Boolean enabled = (Boolean) fields.get("actions_enabled");
    List<String> alarmActions = (List<String>) fields.get("alarm_actions");
    List<String> okActions = (List<String>) fields.get("ok_actions");
    List<String> undeterminedActions = (List<String>) fields.get("undetermined_actions");
    AlarmValidation.validate(name, description, severity, alarmActions, okActions,
        undeterminedActions);
    AlarmExpression alarmExpression =
        expression == null ? null : AlarmValidation.validateNormalizeAndGet(expression);

    return Links.hydrate(service.patch(tenantId, alarmDefinitionId, name, description, severity,
        expression, alarmExpression, matchBy, state, enabled, alarmActions, okActions,
        undeterminedActions), uriInfo, true);
  }

  @DELETE
  @Timed
  @Path("/{alarm_definition_id}")
  @ApiOperation(value = "Delete alarm definition")
  public void delete(@HeaderParam("X-Tenant-Id") String tenantId,
      @PathParam("alarm_definition_id") String alarmDefinitionId) {
    service.delete(tenantId, alarmDefinitionId);
  }
}
