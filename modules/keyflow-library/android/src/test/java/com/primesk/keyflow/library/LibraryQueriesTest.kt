package com.primesk.keyflow.library

import org.junit.Assert.*
import org.junit.Test

class LibraryQueriesTest {
  @Test fun searchIsTokenizedAndCannotInjectFtsOperators() {
    assertEquals("\"hello*\" \"OR*\" \"world*\"", LibraryQueries.searchExpression("hello \" OR (world)*"))
    assertEquals("", LibraryQueries.searchExpression("*** \""))
    assertEquals("\"café*\"", LibraryQueries.searchExpression("café"))
  }
  @Test fun pagesAreBounded() {
    assertEquals(50 to 0, LibraryQueries.page(emptyMap()))
    assertEquals(100 to 0, LibraryQueries.page(mapOf("limit" to 10000, "offset" to -50)))
    assertEquals(1 to 25, LibraryQueries.page(mapOf("limit" to 0, "offset" to 25)))
  }
}
