/**
 * Copyright (c) 2013, impossibl.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of impossibl.com nor the names of its contributors may
 *    be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
/*-------------------------------------------------------------------------
 *
 * Copyright (c) 2004-2011, PostgreSQL Global Development Group
 *
 *
 *-------------------------------------------------------------------------
 */
package com.impossibl.postgres.jdbc;

import com.impossibl.postgres.datetime.instants.FutureInfiniteInstant;
import com.impossibl.postgres.datetime.instants.Instant;
import com.impossibl.postgres.datetime.instants.PastInfiniteInstant;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/*
 * Test get/setTimestamp for both timestamp with time zone and
 * timestamp without time zone datatypes
 *
 */
@RunWith(JUnit4.class)
public class TimestampTest {

  private Connection con;

  @Before
  public void before() throws Exception {
    con = TestUtil.openDB();
    TestUtil.createTable(con, TSWTZ_TABLE, "ts timestamp with time zone");
    TestUtil.createTable(con, TSWOTZ_TABLE, "ts timestamp without time zone");
    TestUtil.createTable(con, DATE_TABLE, "ts date");
  }

  @After
  public void after() throws Exception {
    TestUtil.dropTable(con, TSWTZ_TABLE);
    TestUtil.dropTable(con, TSWOTZ_TABLE);
    TestUtil.dropTable(con, DATE_TABLE);
    TestUtil.closeDB(con);
  }

  /**
   * Ensure the driver doesn't modify a Calendar that is passed in.
   */
  @Test
  public void testCalendarModification() throws SQLException {
    Calendar cal = Calendar.getInstance();
    Calendar origCal = (Calendar) cal.clone();
    PreparedStatement ps = con.prepareStatement("INSERT INTO " + TSWOTZ_TABLE + " VALUES (?)");

    ps.setDate(1, new Date(0), cal);
    ps.executeUpdate();
    assertEquals(origCal, cal);

    ps.setTimestamp(1, new Timestamp(0), cal);
    ps.executeUpdate();
    assertEquals(origCal, cal);

    ps.setTime(1, new Time(0), cal);
    ps.executeUpdate();
    assertEquals(origCal, cal);

    ps.close();
    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT ts FROM " + TSWOTZ_TABLE);
    assertTrue(rs.next());

    rs.getDate(1, cal);
    assertEquals(origCal, cal);

    rs.getTimestamp(1, cal);
    assertEquals(origCal, cal);

    rs.getTime(1, cal);
    assertEquals(origCal, cal);

    rs.close();
    stmt.close();
  }

  @Test
  public void testInfinity() throws SQLException {
    runInfinityTests(TSWTZ_TABLE, FutureInfiniteInstant.INSTANCE);
    runInfinityTests(TSWTZ_TABLE, PastInfiniteInstant.INSTANCE);
    runInfinityTests(TSWOTZ_TABLE, FutureInfiniteInstant.INSTANCE);
    runInfinityTests(TSWOTZ_TABLE, PastInfiniteInstant.INSTANCE);
    runInfinityTests(DATE_TABLE, FutureInfiniteInstant.INSTANCE);
    runInfinityTests(DATE_TABLE, PastInfiniteInstant.INSTANCE);
  }

  private void runInfinityTests(String table, Instant value) throws SQLException {
    GregorianCalendar cal = new GregorianCalendar();
    // Pick some random timezone that is hopefully different than ours
    // and exists in this JVM.
    cal.setTimeZone(TimeZone.getTimeZone("Europe/Warsaw"));

    String strValue = value.print(null);

    Statement stmt = con.createStatement();
    stmt.executeUpdate(TestUtil.insertSQL(table, "'" + strValue + "'"));
    stmt.close();

    PreparedStatement ps = con.prepareStatement(TestUtil.insertSQL(table, "?"));
    ps.setTimestamp(1, value.toTimestamp());
    ps.executeUpdate();
    ps.setTimestamp(1, value.toTimestamp(), cal);
    ps.executeUpdate();
    ps.close();

    stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("select ts from " + table);
    while (rs.next()) {
      assertEquals(strValue, rs.getString(1));

      Timestamp ts = rs.getTimestamp(1);
      assertEquals(value.toTimestamp(), ts);

      Date d = rs.getDate(1);
      assertEquals(value.toDate(), d);

      Timestamp tscal = rs.getTimestamp(1, cal);
      assertEquals(value.toTimestamp(), tscal);
    }
    rs.close();

    assertEquals(3, stmt.executeUpdate("DELETE FROM " + table));
    stmt.close();
  }

  /*
   * Tests the timestamp methods in ResultSet on timestamp with time zone we
   * insert a known string value (don't use setTimestamp) then see that we get
   * back the same value from getTimestamp
   */
  @Test
  public void testGetTimestampWTZ() throws SQLException {
    Statement stmt = con.createStatement();
    TimestampUtils tsu = new TimestampUtils();

    // Insert the three timestamp values in raw pg format
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS1WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS2WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS3WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS4WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS1WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS2WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS3WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS4WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS1WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS2WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS3WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + TS4WTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate1) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate2) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate3) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate4) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime1) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime2) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime3) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime4) + "'")));

    // Fall through helper
    timestampTestWTZ();

    assertEquals(20, stmt.executeUpdate("DELETE FROM " + TSWTZ_TABLE));

    stmt.close();
  }

  /*
   * Tests the timestamp methods in PreparedStatement on timestamp with time
   * zone we insert a value using setTimestamp then see that we get back the
   * same value from getTimestamp (which we know works as it was tested
   * independently of setTimestamp
   */
  @Test
  public void testSetTimestampWTZ() throws SQLException {
    Statement stmt = con.createStatement();
    PreparedStatement pstmt = con.prepareStatement(TestUtil.insertSQL(TSWTZ_TABLE, "?"));

    pstmt.setTimestamp(1, TS1WTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS2WTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS3WTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS4WTZ);
    assertEquals(1, pstmt.executeUpdate());

    // With java.sql.Timestamp
    pstmt.setObject(1, TS1WTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS2WTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS3WTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS4WTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());

    // With Strings
    pstmt.setObject(1, TS1WTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS2WTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS3WTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS4WTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());

    // With java.sql.Date
    pstmt.setObject(1, tmpDate1, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate2, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate3, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate4, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());

    // With java.sql.Time
    pstmt.setObject(1, tmpTime1, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime2, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime3, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime4, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    // Fall through helper
    timestampTestWTZ();

    assertEquals(20, stmt.executeUpdate("DELETE FROM " + TSWTZ_TABLE));

    pstmt.close();
    stmt.close();
  }

  /*
   * Tests the timestamp methods in ResultSet on timestamp without time zone we
   * insert a known string value (don't use setTimestamp) then see that we get
   * back the same value from getTimestamp
   */
  @Test
  public void testGetTimestampWOTZ() throws SQLException {
    Statement stmt = con.createStatement();
    TimestampUtils tsu = new TimestampUtils();

    // Insert the three timestamp values in raw pg format
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS1WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS2WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS3WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS4WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS5WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS6WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS1WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS2WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS3WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS4WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS5WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS6WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS1WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS2WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS3WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS4WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS5WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + TS6WOTZ_PGFORMAT + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate1WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate2WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate3WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate4WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate5WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpDate6WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime1WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime2WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime3WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime4WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime5WOTZ) + "'")));
    assertEquals(1, stmt.executeUpdate(TestUtil.insertSQL(TSWOTZ_TABLE, "'" + tsu.toTimestampString(null, tmpTime6WOTZ) + "'")));

    // Fall through helper
    timestampTestWOTZ();

    assertEquals(30, stmt.executeUpdate("DELETE FROM " + TSWOTZ_TABLE));

    stmt.close();
  }

  /*
   * Tests the timestamp methods in PreparedStatement on timestamp without time
   * zone we insert a value using setTimestamp then see that we get back the
   * same value from getTimestamp (which we know works as it was tested
   * independently of setTimestamp
   */
  @Test
  public void testSetTimestampWOTZ() throws SQLException {
    Statement stmt = con.createStatement();
    PreparedStatement pstmt = con.prepareStatement(TestUtil.insertSQL(TSWOTZ_TABLE, "?"));

    pstmt.setTimestamp(1, TS1WOTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS2WOTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS3WOTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS4WOTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS5WOTZ);
    assertEquals(1, pstmt.executeUpdate());

    pstmt.setTimestamp(1, TS6WOTZ);
    assertEquals(1, pstmt.executeUpdate());

    // With java.sql.Timestamp
    pstmt.setObject(1, TS1WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS2WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS3WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS4WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS5WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS6WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());

    // With Strings
    pstmt.setObject(1, TS1WOTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS2WOTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS3WOTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS4WOTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS5WOTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, TS6WOTZ_PGFORMAT, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());

    // With java.sql.Date
    pstmt.setObject(1, tmpDate1WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate2WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate3WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate4WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate5WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpDate6WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());

    // With java.sql.Time
    pstmt.setObject(1, tmpTime1WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime2WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime3WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime4WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime5WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    pstmt.setObject(1, tmpTime6WOTZ, java.sql.Types.TIMESTAMP);
    assertEquals(1, pstmt.executeUpdate());
    // Fall through helper
    timestampTestWOTZ();

    assertEquals(30, stmt.executeUpdate("DELETE FROM " + TSWOTZ_TABLE));

    pstmt.close();
    stmt.close();
  }

  /*
   * Helper for the TimestampTests. It tests what should be in the db
   */
  private void timestampTestWTZ() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet rs;
    java.sql.Timestamp t;

    rs = stmt.executeQuery("select ts from " + TSWTZ_TABLE); // removed the
                                                              // order by ts
    assertNotNull(rs);

    for (int i = 0; i < 3; i++) {
      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS1WTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS2WTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS3WTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS4WTZ, t);
    }

    // Testing for Date
    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate1.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate2.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate3.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate4.getTime(), t.getTime());

    // Testing for Time
    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime1.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime2.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime3.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime4.getTime(), t.getTime());

    assertTrue(!rs.next()); // end of table. Fail if more entries exist.

    rs.close();
    stmt.close();
  }

  /*
   * Helper for the TimestampTests. It tests what should be in the db
   */
  private void timestampTestWOTZ() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet rs;
    java.sql.Timestamp t;

    rs = stmt.executeQuery("select ts from " + TSWOTZ_TABLE); // removed the
                                                              // order by ts
    assertNotNull(rs);

    for (int i = 0; i < 3; i++) {
      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS1WOTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS2WOTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS3WOTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS4WOTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS5WOTZ, t);

      assertTrue(rs.next());
      t = rs.getTimestamp(1);
      assertNotNull(t);
      assertEquals(TS6WOTZ, t);
    }

    // Testing for Date
    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate1WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate2WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate3WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpDate4WOTZ.getTime(), t.getTime());

    /*
     * Coercing infinity values to dates is not supported
     * so the checks below would always fail with crazy values
     */

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
//    assertEquals(tmpDate5WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
//    assertEquals(tmpDate6WOTZ.getTime(), t.getTime());

    // Testing for Time
    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime1WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime2WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime3WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
    assertEquals(tmpTime4WOTZ.getTime(), t.getTime());

    /*
     * Coercing infinity values to dates is not supported
     * so the checks below would always fail with crazy values
     */

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
//    assertEquals(tmpTime5WOTZ.getTime(), t.getTime());

    assertTrue(rs.next());
    t = rs.getTimestamp(1);
    assertNotNull(t);
//    assertEquals(tmpTime6WOTZ.getTime(), t.getTime());

    assertTrue(!rs.next()); // end of table. Fail if more entries exist.

    rs.close();
    stmt.close();
  }

  private static java.sql.Timestamp getTimestamp(int y, int m, int d, int h, int mn, int se, int f, String tz) {
    java.sql.Timestamp l_return = null;
    java.text.DateFormat l_df;
    try {
      String l_ts;
      l_ts = TestUtil.fix(y, 4) + "-" + TestUtil.fix(m, 2) + "-" + TestUtil.fix(d, 2) + " " + TestUtil.fix(h, 2) + ":" + TestUtil.fix(mn, 2) + ":" + TestUtil.fix(se, 2) + " ";

      if (tz == null) {
        l_df = new java.text.SimpleDateFormat("y-M-d H:m:s");
      }
      else {
        l_ts = l_ts + tz;
        l_df = new java.text.SimpleDateFormat("y-M-d H:m:s z");
      }
      java.util.Date l_date = l_df.parse(l_ts);
      l_return = new java.sql.Timestamp(l_date.getTime());
      l_return.setNanos(f);
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    return l_return;
  }

  private static final java.sql.Timestamp TS1WTZ = getTimestamp(1950, 2, 7, 15, 0, 0, 100000000, "PST");
  private static final String TS1WTZ_PGFORMAT = "1950-02-07 15:00:00.1-08";

  private static final java.sql.Timestamp TS2WTZ = getTimestamp(2000, 2, 7, 15, 0, 0, 120000000, "GMT");
  private static final String TS2WTZ_PGFORMAT = "2000-02-07 15:00:00.12+00";

  private static final java.sql.Timestamp TS3WTZ = getTimestamp(2000, 7, 7, 15, 0, 0, 123000000, "GMT");
  private static final String TS3WTZ_PGFORMAT = "2000-07-07 15:00:00.123+00";

  private static final java.sql.Timestamp TS4WTZ = getTimestamp(2000, 7, 7, 15, 0, 0, 123456000, "GMT");
  private static final String TS4WTZ_PGFORMAT = "2000-07-07 15:00:00.123456+00";

  private static final java.sql.Timestamp TS1WOTZ = getTimestamp(1950, 2, 7, 15, 0, 0, 100000000, null);
  private static final String TS1WOTZ_PGFORMAT = "1950-02-07 15:00:00.1";

  private static final java.sql.Timestamp TS2WOTZ = getTimestamp(2000, 2, 7, 15, 0, 0, 120000000, null);
  private static final String TS2WOTZ_PGFORMAT = "2000-02-07 15:00:00.12";

  private static final java.sql.Timestamp TS3WOTZ = getTimestamp(2000, 7, 7, 15, 0, 0, 123000000, null);
  private static final String TS3WOTZ_PGFORMAT = "2000-07-07 15:00:00.123";

  private static final java.sql.Timestamp TS4WOTZ = getTimestamp(2000, 7, 7, 15, 0, 0, 123456000, null);
  private static final String TS4WOTZ_PGFORMAT = "2000-07-07 15:00:00.123456";

  private static final java.sql.Timestamp TS5WOTZ = PastInfiniteInstant.INSTANCE.toTimestamp();
  private static final String TS5WOTZ_PGFORMAT = "-infinity";

  private static final java.sql.Timestamp TS6WOTZ = FutureInfiniteInstant.INSTANCE.toTimestamp();
  private static final String TS6WOTZ_PGFORMAT = "infinity";

  private static final String TSWTZ_TABLE = "testtimestampwtz";
  private static final String TSWOTZ_TABLE = "testtimestampwotz";
  private static final String DATE_TABLE = "testtimestampdate";

  private static final java.sql.Date tmpDate1 = new java.sql.Date(TS1WTZ.getTime());
  private static final java.sql.Time tmpTime1 = new java.sql.Time(TS1WTZ.getTime());
  private static final java.sql.Date tmpDate2 = new java.sql.Date(TS2WTZ.getTime());
  private static final java.sql.Time tmpTime2 = new java.sql.Time(TS2WTZ.getTime());
  private static final java.sql.Date tmpDate3 = new java.sql.Date(TS3WTZ.getTime());
  private static final java.sql.Time tmpTime3 = new java.sql.Time(TS3WTZ.getTime());
  private static final java.sql.Date tmpDate4 = new java.sql.Date(TS4WTZ.getTime());
  private static final java.sql.Time tmpTime4 = new java.sql.Time(TS4WTZ.getTime());

  private static final java.sql.Date tmpDate1WOTZ = new java.sql.Date(TS1WOTZ.getTime());
  private static final java.sql.Time tmpTime1WOTZ = new java.sql.Time(TS1WOTZ.getTime());
  private static final java.sql.Date tmpDate2WOTZ = new java.sql.Date(TS2WOTZ.getTime());
  private static final java.sql.Time tmpTime2WOTZ = new java.sql.Time(TS2WOTZ.getTime());
  private static final java.sql.Date tmpDate3WOTZ = new java.sql.Date(TS3WOTZ.getTime());
  private static final java.sql.Time tmpTime3WOTZ = new java.sql.Time(TS3WOTZ.getTime());
  private static final java.sql.Date tmpDate4WOTZ = new java.sql.Date(TS4WOTZ.getTime());
  private static final java.sql.Time tmpTime4WOTZ = new java.sql.Time(TS4WOTZ.getTime());
  private static final java.sql.Date tmpDate5WOTZ = PastInfiniteInstant.INSTANCE.toDate();
  private static final java.sql.Date tmpTime5WOTZ = PastInfiniteInstant.INSTANCE.toDate();
  private static final java.sql.Date tmpDate6WOTZ = FutureInfiniteInstant.INSTANCE.toDate();
  private static final java.sql.Date tmpTime6WOTZ = FutureInfiniteInstant.INSTANCE.toDate();

}
