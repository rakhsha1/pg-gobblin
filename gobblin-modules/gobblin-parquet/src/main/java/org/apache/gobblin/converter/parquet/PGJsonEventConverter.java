
package org.apache.gobblin.converter.parquet;

import org.apache.gobblin.configuration.WorkUnitState;
import org.apache.gobblin.converter.Converter;
import org.apache.gobblin.converter.DataConversionException;
import org.apache.gobblin.converter.SchemaConversionException;
import org.apache.gobblin.converter.SingleRecordIterable;
import com.google.common.base.Optional;
import org.apache.gobblin.configuration.ConfigurationKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import gobblin.util.PGStatsClient;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;


import java.math.RoundingMode;
import java.math.BigDecimal;

import java.nio.charset.StandardCharsets;
import java.io.File;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;

import com.google.common.collect.Iterators;

public class PGJsonEventConverter extends Converter<String, JsonArray, byte [], JsonObject> {
  private static final Logger LOG = LoggerFactory.getLogger(PGJsonEventConverter.class);
  private PGStatsClient statsClient;
  private String schema = "[{\"columnName\":\"timestamp\",\"dataType\":{\"type\":\"long\"}},{\"columnName\":\"value\",\"dataType\":{\"type\":\"string\"}}]";
  private Set<String> locationTopics;

  private int locationPrecision;
  private JsonArray convertedSchema = new JsonParser().parse(schema).getAsJsonArray();

  @Override
  public Converter<String, JsonArray, byte [], JsonObject> init(WorkUnitState workUnit) {
    statsClient = new PGStatsClient(workUnit.getProp(ConfigurationKeys.METRICS_HOST), workUnit.getProp(ConfigurationKeys.METRICS_PORT), workUnit.getProp(ConfigurationKeys.METRICS_ENABLED));
    String topics = workUnit.getProp(ConfigurationKeys.LOCATION_TOPICS, "");
    locationTopics = new HashSet<String>(Arrays.asList(topics.split(",")));
    locationPrecision = workUnit.getPropAsInt(ConfigurationKeys.LOCATION_PRECISION, 3);
    return this;
  }

  @Override
  public void close() throws IOException {
    statsClient.flush();
  }

  @Override
  public JsonArray convertSchema(String inputSchema, WorkUnitState workUnit) throws SchemaConversionException {
    return convertedSchema;
  }

  private String processEvent(String event, String topic) {
    if(!locationTopics.contains(topic)){
      return event;
    }
    JsonObject eventObject;
    try{
      eventObject = new JsonParser().parse(event).getAsJsonObject();  
    }catch(Exception e){
      return null;
    }
    
    Double lat = (eventObject.has("lat")) ? eventObject.get("lat").getAsDouble() : 0.0;
    Double lon = (eventObject.has("lon")) ? eventObject.get("lon").getAsDouble() : 0.0;
   
    Double roundedLat = new BigDecimal(lat).setScale(locationPrecision, RoundingMode.HALF_UP).doubleValue();
    if(roundedLat != 0.0){
      eventObject.addProperty("lat", roundedLat);
    }
    Double roundedLon = new BigDecimal(lon).setScale(locationPrecision, RoundingMode.HALF_UP).doubleValue();
    if(roundedLon != 0.0){
      eventObject.addProperty("lon", roundedLon);
    }

    return eventObject.toString(); 
  }

  @Override
  public Iterable<JsonObject> convertRecord(JsonArray outputSchema, byte[] inputRecord, WorkUnitState workUnit)
      throws DataConversionException {
    String topic = workUnit.getExtract().getTable();
    statsClient.pushCounter(topic, 1);
    
    String event = processEvent(new String(inputRecord), topic);
    if(event == null){
      return new ArrayList<JsonObject>();
    }
    
    JsonObject outputRecord = new JsonObject();
    outputRecord.addProperty("timestamp", System.currentTimeMillis());
    outputRecord.addProperty("value", event);
    return new SingleRecordIterable<JsonObject>(outputRecord);
  }
}
