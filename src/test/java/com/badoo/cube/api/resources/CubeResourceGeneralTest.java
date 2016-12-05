package com.badoo.cube.api.resources;

import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.cubedb.api.resources.CubeResource;
import org.cubedb.api.utils.APIResponse;
import org.cubedb.core.Constants;
import org.cubedb.core.MultiCube;
import org.cubedb.core.MultiCubeImpl;
import org.cubedb.core.beans.DataRow;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badoo.cube.utils.TestUtils;
import com.owlike.genson.GenericType;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;

public class CubeResourceGeneralTest {
	private HttpServer httpServer;
	private WebTarget webTarget;
	private MultiCube cube;
	private static final URI baseUri = URI.create("http://localhost:9090/rest/");

	public static final Logger log = LoggerFactory.getLogger(CubeResourceGeneralTest.class);
	//public static final savePath =



	@Before
	public void setup() throws Exception {
	    //create ResourceConfig from Resource class
	    ResourceConfig rc = new ResourceConfig();
	    cube = new MultiCubeImpl(null);
	    rc.registerInstances(new CubeResource(cube));

	    //create the Grizzly server instance
	    httpServer = GrizzlyHttpServerFactory.createHttpServer(baseUri, rc);
	    //start the server
	    httpServer.start();

	    //configure client with the base URI path
	    Client client = ClientBuilder.newClient();
	    webTarget = client.target(baseUri);
	}

	@After
	public void tearDown() throws Exception {
		httpServer.shutdown();
	}



	@Test
	public void testInsertAndGet() {
		//Response r = null;
		Genson builder = new GensonBuilder().useIndentation(true).create();
		String cubeName = "AppStart_hour";
		int numFields = 2;
		int numValues = 3;
		int numPartitions = 2;
		for(int i=0;i<numPartitions;i++){
			String partitionName = "2016-09-22 0"+(i);
			List<DataRow> data = TestUtils.genMultiColumnData(cubeName, partitionName, "f", numFields, numValues);
			log.info("Inserting {} rows...", data.size());
			//log.info(builder.serialize(data));
			Entity<List<DataRow>> entity = Entity.entity(data, MediaType.APPLICATION_JSON_TYPE);
			String response = webTarget.path("v1/insert")
				.request().post(entity,String.class);

			APIResponse<Map<String, Integer>> outInsert = new Genson().deserialize(response, new GenericType<APIResponse<Map<String, Integer>>>(){});
		}
		String response = webTarget.path("v1/"+cubeName+"/last/120")
			.queryParam("f_1", "f_1_0")
			.queryParam("f_1", "f_1_1")
			.request().get(String.class);// .get();

		APIResponse<Map<String, Map<String, Map<String, Long>>>> outGet = new Genson().deserialize(response, new GenericType<APIResponse<Map<String, Map<String, Map<String, Long>>>>>(){});
		log.info("{}", outGet);
		//log.info(builder.serialize(outGet));
	}

	@Test
	public void nonObjectTest(){
		String in ="[{\"fields\":{\"gender\":2,\"brand\":2,\"message_first\":true,\"platform\":4,\"app_version\":\"2.61.0\",\"activation_place\":null,\"gift_button\":1},\"cubeName\":\"event_cube_100:hour\",\"partition\":\"2016-09-17 03\",\"counters\":{\"c\":3}}]";
		List<DataRow> data = new Genson().deserialize(in, new GenericType<List<DataRow>>(){});
		log.info("{}", data);
		String cubeName = data.get(0).getCubeName();
		assertNull(data.get(0).getFields().get("activation_place"));
		Entity<List<DataRow>> entity = Entity.entity(data, MediaType.APPLICATION_JSON_TYPE);
		String response = webTarget.path("v1/insert")
			.request().post(entity,String.class);

		APIResponse<Map<String, Integer>> outInsert = new Genson().deserialize(response, new GenericType<APIResponse<Map<String, Integer>>>(){});

		response = webTarget.path("v1/"+cubeName+"/last/120")
			.queryParam("app_version", "2.61.0")
			//.queryParam("platform", "4")
			.request().get(String.class);// .get();

		APIResponse<Map<String, Map<String, Map<String, Long>>>> outGet = new Genson().deserialize(response, new GenericType<APIResponse<Map<String, Map<String, Map<String, Long>>>>>(){});
		log.info("{}", outGet);
	}

	@Test
	public void testInsertCacheExpireAndStats() throws InterruptedException {
		//Response r = null;
		Constants.KEY_MAP_TTL = 50;
		Genson builder = new GensonBuilder().useIndentation(true).create();
		int numFields = 2;
		int numValues = 3;
		int numPartitions = 5;
		int numCubes = 3;
		for(int c=0;c<numCubes;c++){
			for(int i=0;i<numPartitions;i++){
				String partitionName = "2016-09-22 0"+(i);
				String cubeName="cubeName_"+c;
				List<DataRow> data = TestUtils.genMultiColumnData(cubeName, partitionName, "f", numFields, numValues);
				log.info("Inserting {} rows...", data.size());
				//log.info(builder.serialize(data));
				Entity<List<DataRow>> entity = Entity.entity(data, MediaType.APPLICATION_JSON_TYPE);
				String response = webTarget.path("v1/insert")
					.request().post(entity,String.class);

				APIResponse<Map<String, Integer>> outInsert = new Genson().deserialize(response, new GenericType<APIResponse<Map<String, Integer>>>(){});
			}
		}

		Thread.sleep(Constants.KEY_MAP_TTL + 1);
		String responseBeforeOptimization = webTarget.path("v1/stats")
			.request().get(String.class);// .get();

		APIResponse<Map<String, Object>> outStats = new Genson().deserialize(responseBeforeOptimization, new GenericType<APIResponse<Map<String, Object>>>(){});
		log.info("{}", outStats);

		webTarget.path("v1/stats")
			.request().get(String.class);

		int numOptimizedPartitions = this.cube.optimize();
		log.info("Optimized {} partitions", numOptimizedPartitions);
		log.info("Stats: {}", this.cube.getStats());
		String responseAfterOptimization = webTarget.path("v1/stats")
			.request().get(String.class);// .get();
		APIResponse<Map<String, Object>> outStatsAfter = new Genson().deserialize(responseBeforeOptimization, new GenericType<APIResponse<Map<String, Object>>>(){});
		log.info("{}", outStatsAfter);
		//log.info(builder.serialize(outGet));
	}
}
