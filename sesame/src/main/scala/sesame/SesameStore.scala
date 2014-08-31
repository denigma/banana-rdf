package org.w3.banana.sesame

import org.w3.banana._
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.util.Try
import org.openrdf.model._
import org.openrdf.model.impl._
import org.openrdf.repository.{ Repository, RepositoryConnection, RepositoryResult }
import org.openrdf.query._

class SesameStore extends RDFStore[Sesame, RepositoryConnection] {

  import scala.concurrent.ExecutionContext.Implicits.global

  /* Transactor */

  def r[T](conn: RepositoryConnection, body: => T): Try[T] = ???

  def rw[T](conn: RepositoryConnection, body: => T): Try[T] = ???

  /* SparqlEngine */

  /**
   * Watch out connection is not closed here and neither is iterator.
   * (what does that mean? please help out)
   */
  def executeSelect(conn: RepositoryConnection, query: Sesame#SelectQuery, bindings: Map[String, Sesame#Node]): Future[Sesame#Solutions] = Future {
    val accumulator = new BindingsAccumulator()
    val tupleQuery: TupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query.getSourceString)
    bindings foreach { case (name, value) => tupleQuery.setBinding(name, value) }
    tupleQuery.evaluate(accumulator)
    accumulator.bindings()
  }

  def executeConstruct(conn: RepositoryConnection, query: Sesame#ConstructQuery, bindings: Map[String, Sesame#Node]): Future[Sesame#Graph] = Future {
    val graphQuery: GraphQuery = conn.prepareGraphQuery(QueryLanguage.SPARQL, query.getSourceString)
    bindings foreach { case (name, value) => graphQuery.setBinding(name, value) }
    val result: GraphQueryResult = graphQuery.evaluate()
    val graph = new LinkedHashModel
    while (result.hasNext) {
      graph.add(result.next())
    }
    result.close()
    graph
  }

  def executeAsk(conn: RepositoryConnection, query: Sesame#AskQuery, bindings: Map[String, Sesame#Node]): Future[Boolean] = Future {
    val booleanQuery: BooleanQuery = conn.prepareBooleanQuery(QueryLanguage.SPARQL, query.getSourceString)
    bindings foreach { case (name, value) => booleanQuery.setBinding(name, value) }
    val result: Boolean = booleanQuery.evaluate()
    result
  }

  /** TODO shouldn't be here... */
  def executeUpdate(conn: RepositoryConnection, query: Sesame#UpdateQuery, bindings: Map[String, Sesame#Node]): Unit = {
    val updateQuery = conn.prepareUpdate(QueryLanguage.SPARQL, query.query)
    bindings foreach { case (name, value) => updateQuery.setBinding(name, value) }
    updateQuery.execute()
  }

  /* GraphStore */

  def appendToGraph(conn: RepositoryConnection, uri: Sesame#URI, graph: Sesame#Graph): Future[Unit] = Future {
    val triples = RDFOps[Sesame].graphToIterable(graph).asJava
    conn.add(triples, uri)
  }

  def removeTriples(conn: RepositoryConnection, uri: Sesame#URI, tripleMatches: Iterable[TripleMatch[Sesame]]): Future[Unit] = Future {
    val ts = tripleMatches.map { case (s, p, o) =>
      new StatementImpl(s.asInstanceOf[Resource], p.asInstanceOf[URI], o)
    }
    conn.remove(ts.asJava, uri)
  }

  def getGraph(conn: RepositoryConnection, uri: Sesame#URI): Future[Sesame#Graph] = Future {
    val graph = new LinkedHashModel
    val rr: RepositoryResult[Statement] = conn.getStatements(null, null, null, false, uri)
    while (rr.hasNext) {
      val s = rr.next()
      graph.add(s)
    }
    rr.close()
    graph
  }

  def removeGraph(conn: RepositoryConnection, uri: Sesame#URI): Future[Unit] = Future {
    conn.remove(null: Resource, null, null, uri)
  }

}

