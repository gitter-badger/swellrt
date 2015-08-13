package org.swellrt.server.box.servlet;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import org.swellrt.server.box.SwellRtModule;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.mongodb.MongoDbProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A Servlet providing SwellRt REST operations.
 *
 * TODO: to use a "cool" REST framework
 *
 *
 * @author pablojan@gmail.com
 *
 */
@Singleton
@SuppressWarnings("serial")
public class SwellRtServlet extends HttpServlet {

  private static final Log LOG = Log.get(SwellRtServlet.class);

  private final SessionManager sessionManager;
  // TODO replace with a Persistence Class. Avoid direct access to MongoDb
  // Collection here.
  private DBCollection store;

  @Inject
  public SwellRtServlet(SessionManager sessionManager, MongoDbProvider mongoDbProvider) {
    this.sessionManager = sessionManager;
    try {
      this.store = mongoDbProvider.getDBCollection(SwellRtModule.MONGO_COLLECTION);
    } catch (Exception e) {
      LOG.warning("Unable to get MongoDB collection. SwellRT servlet won't work!", e);
      this.store = null;
    }
  }

  private DBObject parseParam(String param) throws IOException {

    DBObject objectQuery = null;
    if (param == null || param.isEmpty()) {
      objectQuery = new BasicDBObject();
    } else {
      objectQuery = (DBObject) JSON.parse(param);
    }
    return objectQuery;
  }

  private DBCursor getQueryResult(DBObject objectQuery, DBObject objectProjection,
      BasicDBList limitPartQuery) {

    DBCursor result;

    objectQuery.put("$or",limitPartQuery);

    // exclude internal mongoDb _id

    objectProjection.put("_id", 0);
    // You cannot currently mix including and excluding fields
    if (!objectProjection.toMap().containsValue(1)) {
      objectProjection.put("wavelet_id", 0);
    }

    result = store.find(objectQuery, objectProjection);

    return result;
  }


  private Iterable<DBObject> getAggregateResult(DBObject objectAggregate, BasicDBList limitPartQuery) {
    Iterable<DBObject> result;

    DBObject objectQuery = new BasicDBObject();
    objectQuery.put("$or", limitPartQuery);
    DBObject matchQuery = new BasicDBObject();
    matchQuery.put("$match", objectQuery);

    ArrayList<DBObject> args = new ArrayList<DBObject>();

    Iterator<String> i = objectAggregate.keySet().iterator();
    while (i.hasNext()) {
      args.add((DBObject) objectAggregate.get(i.next()));
    }

    DBObject[] argsArray = new DBObject[args.size()];
    args.toArray(argsArray);

    AggregationOutput r = store.aggregate(matchQuery, argsArray);
    result = r.results();
    return result;
  }


  /**
   * Create an http response to the fetch query. Main entrypoint for this class.
   */
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));

    if (store == null) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error accesing data model index");
      return;
    }

    if (user == null) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    // Either... /rest/[api_version]/model?q={MongoDB query}?p={MongoDB
    // projection}
    // or... /rest/[api_version]/model?a={MongoDB aggregate query}

    String pathInfo = req.getPathInfo();
    String entity = pathInfo.substring(pathInfo.lastIndexOf("/") + 1, pathInfo.length());

    if (!entity.equals("model")) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    String aggregate = req.getParameter("a");

    DBObject objectAggregate = null;
    DBObject objectQuery = null;
    DBObject objectProjection = null;

    BasicDBList limitPartQuery = new BasicDBList();
    // Get models where user is participant
    limitPartQuery.add(new BasicDBObject("participants", user.getAddress()));
    // Get public models
    limitPartQuery.add(new BasicDBObject("participants", "@" + user.getDomain()));

    Iterable<DBObject> result;

    // Aggregate Case:
    if (aggregate != null) {
      objectAggregate = parseParam(aggregate);

      if (objectAggregate == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad aggregate JSON format");
        return;
      }
      result = getAggregateResult(objectAggregate, limitPartQuery);
    }
    // Query or Query + Projection Case:
    else {
      String query = req.getParameter("q");

      if (query != null) {
        objectQuery = parseParam(query);
        if (objectQuery == null) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad JSON format");
          return;
        }
      }

      String projection = req.getParameter("p");

      if (projection != null) {
        objectProjection = parseParam(projection);

        if (objectProjection == null) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad JSON format");
          return;
        }
      } else {
        objectProjection = new BasicDBObject();
      }
      result = getQueryResult(objectQuery, objectProjection, limitPartQuery);
    }

    StringBuilder JSONbuilder = new StringBuilder();
    JSONbuilder.append("{\"result\":[");

    Iterator<DBObject> it = result.iterator();

    while (it.hasNext()) {

      JSON.serialize(it.next(), JSONbuilder);
      if (it.hasNext()) JSONbuilder.append(",");
    }
    JSONbuilder.append("]}");

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");

    response.setHeader("Cache-Control", "no-store");
    response.getWriter().append(JSONbuilder);

  }

}
