/*
 * Copyright 2009-2013 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package org.zoodb.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Iterator;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.zoodb.jdo.api.ZooClass;
import org.zoodb.jdo.api.ZooHandle;
import org.zoodb.jdo.api.ZooSchema;
import org.zoodb.test.testutil.TestTools;

public class Test_037_SchemaWriting {

	@Before
	public void before() {
		TestTools.createDb();
		TestTools.defineSchema(TestClass.class);
	}
	
	@AfterClass
	public static void tearDown() {
		TestTools.removeDb();
	}

	@After
	public void after() {
		TestTools.closePM();
	}
	
	@Test
	public void testSchemaZooHandle() {
		PersistenceManager pm0 = TestTools.openPM();
		pm0.currentTransaction().begin();
		
		ZooClass cls = ZooSchema.locateClass(pm0, TestClass.class.getName());
		
		ZooHandle hdl01 = cls.newInstance();
		ZooHandle hdl02 = cls.newInstance();
		
		long oid1 = (Long) hdl01.getOid();
		long oid2 = (Long) hdl02.getOid();
		
		pm0.currentTransaction().commit();
		TestTools.closePM();
		
		PersistenceManager pm = TestTools.openPM();
		pm.currentTransaction().begin();
		
		ZooClass s01 = ZooSchema.locateClass(pm, TestClass.class.getName());
		assertNotNull(s01);

		//closed pm
		try {
			ZooSchema.getHandle(pm0, oid1);
			fail();
		} catch (IllegalStateException e) {
			//good!
		}
		
		//wrong oid
		assertNull(ZooSchema.getHandle(pm, 12345678));
		
		ZooHandle hdl1 = ZooSchema.getHandle(pm, oid1);
		ZooHandle hdl2 = ZooSchema.getHandle(pm, oid2);
		assertNotNull(hdl1);
		assertNotNull(hdl2);
		
		assertEquals((Long)oid1, (Long)hdl1.getOid());
		assertEquals(s01, hdl1.getType());
		
		TestTools.closePM();
	}
	

	@Test
	public void testSchemaZooHandleWriter() {
		PersistenceManager pm0 = TestTools.openPM();
		pm0.currentTransaction().begin();
		
		ZooClass cls = ZooSchema.locateClass(pm0, TestClass.class.getName());
		
		ZooHandle hdl01 = cls.newInstance();
		ZooHandle hdl02 = cls.newInstance();

//		TestClass t1 = new TestClass();
//		t1.setData(1234567, true, 'x', (byte)126, (short)32000, 12345678901L, "haha", 
//				new byte[]{1, 2, 3}, -1.1f, 33.3);
		hdl01.setValue("_int", 1234567);
		hdl01.setValue("_bool", true);
		hdl01.setValue("_char", 'x');
		hdl01.setValue("_byte", (byte)126);
		hdl01.setValue("_short", (short)32000);
		hdl01.setValue("_long", 12345678901L);
		hdl01.setValue("_string", "haha");
		//TODO hdl01.setValue("_bArray", new byte[]{1, 2, 3});
		System.err.println("FIXME: Setting SCOs");
		hdl01.setValue("_float", -1.1f);
		hdl01.setValue("_double", 33.3);
//		TestClass t2 = new TestClass();
//		t1.setRef2(t2);
		hdl01.setValue("_ref2", hdl02);
		
		long oid1 = hdl01.getOid();
		long oid2 = hdl02.getOid();
		
		pm0.currentTransaction().commit();
		TestTools.closePM();
		
		PersistenceManager pm = TestTools.openPM();
		pm.currentTransaction().begin();
		
		ZooClass s01 = ZooSchema.locateClass(pm, TestClass.class.getName());
		assertNotNull(s01);

		ZooHandle hdl1 = ZooSchema.getHandle(pm, oid1);

		assertEquals(126, hdl1.getAttrByte("_byte"));
		assertEquals(1234567, hdl1.getAttrInt("_int"));
		assertEquals(true, hdl1.getAttrBool("_bool"));
		assertEquals('x', hdl1.getAttrChar("_char"));
		assertEquals(32000, hdl1.getAttrShort("_short"));
		assertEquals(12345678901L, hdl1.getAttrLong("_long"));
		assertEquals(-1.1f, hdl1.getAttrFloat("_float"), 0.0);
		assertEquals(33.3, hdl1.getAttrDouble("_double"), 0.0);
		assertEquals("haha", hdl1.getAttrString("_string"));
		
		long oid2b = hdl1.getAttrRefOid("_ref2");
		assertEquals(oid2, oid2b);
		
		TestTools.closePM();
	}

	@Test
	public void testGenericObjectIndexUpdatesWithCommit() {
		TestTools.defineIndex(TestClass.class, "_int", true);
		
		PersistenceManager pm0 = TestTools.openPM();
		pm0.currentTransaction().begin();
		
		TestClass t1 = new TestClass();
		t1.setData(7, true, 'x', (byte)126, (short)32000, 12345678901L, "haha", 
				new byte[]{1, 2, 3}, -1.1f, 33.3);
		pm0.makePersistent(t1);
		pm0.currentTransaction().commit();
		TestClass t2 = new TestClass();
		t1.setRef2(t2);
		
		long oid1 = (Long) pm0.getObjectId(t1);
		long oid2 = (Long) pm0.getObjectId(t2);

		//commit, no new session
		pm0.currentTransaction().commit();
		pm0.currentTransaction().begin();
		
		ZooHandle hdl01 = ZooSchema.getHandle(pm0, oid1);
		ZooHandle hdl02 = ZooSchema.getHandle(pm0, oid1);

		hdl01.setValue("_int", 12);
		hdl02.setValue("_int", 13);
		
		pm0.currentTransaction().commit();
		TestTools.closePM();

		//query
		PersistenceManager pm = TestTools.openPM();
		pm.currentTransaction().begin();

		Query q = pm.newQuery(TestClass.class, "_int < 12");
		Collection<?> c = (Collection<?>) q.execute();
		assertEquals(0, c.size());

		q = pm.newQuery(TestClass.class, "_int >= 12");
		c = (Collection<?>) q.execute();
		assertEquals(2, c.size());
		Iterator<?> it = c.iterator(); 
		assertEquals(oid1, pm.getObjectId(it.next()));
		assertEquals(oid2, pm.getObjectId(it.next()));
		
		TestTools.closePM();
	}

	@Test
	public void testGenericObjectIndexUpdatesWithOpenClose() {
		TestTools.defineIndex(TestClass.class, "_int", true);
		
		PersistenceManager pm0 = TestTools.openPM();
		pm0.currentTransaction().begin();
		
		TestClass t1 = new TestClass();
		t1.setData(7, true, 'x', (byte)126, (short)32000, 12345678901L, "haha", 
				new byte[]{1, 2, 3}, -1.1f, 33.3);
		pm0.makePersistent(t1);
		pm0.currentTransaction().commit();
		TestClass t2 = new TestClass();
		t1.setRef2(t2);
		
		long oid1 = (Long) pm0.getObjectId(t1);
		long oid2 = (Long) pm0.getObjectId(t2);

		//new session
		pm0.currentTransaction().commit();
		TestTools.closePM();
		pm0 = TestTools.openPM();
		pm0.currentTransaction().begin();
		
		ZooHandle hdl01 = ZooSchema.getHandle(pm0, oid1);
		ZooHandle hdl02 = ZooSchema.getHandle(pm0, oid1);

		hdl01.setValue("_int", 12);
		hdl02.setValue("_int", 13);
		
		pm0.currentTransaction().commit();
		TestTools.closePM();

		//query
		PersistenceManager pm = TestTools.openPM();
		pm.currentTransaction().begin();

		Query q = pm.newQuery(TestClass.class, "_int < 12");
		Collection<?> c = (Collection<?>) q.execute();
		assertEquals(0, c.size());

		q = pm.newQuery(TestClass.class, "_int >= 12");
		c = (Collection<?>) q.execute();
		assertEquals(2, c.size());
		Iterator<?> it = c.iterator(); 
		assertEquals(oid1, pm.getObjectId(it.next()));
		assertEquals(oid2, pm.getObjectId(it.next()));
		
		TestTools.closePM();
	}

	@Test
	public void testGenericObjectIndexUpdatesNewInstance() {
		TestTools.defineIndex(TestClass.class, "_int", true);

		PersistenceManager pm0 = TestTools.openPM();
		pm0.currentTransaction().begin();
		
		ZooClass cls = ZooSchema.locateClass(pm0, TestClass.class.getName());
		
		ZooHandle hdl01 = cls.newInstance();
		ZooHandle hdl02 = cls.newInstance();

		hdl01.setValue("_int", 12);
		hdl02.setValue("_int", 13);
		
		long oid1 = hdl01.getOid();
		long oid2 = hdl02.getOid();
		
		pm0.currentTransaction().commit();
		TestTools.closePM();
		
		//query
		PersistenceManager pm = TestTools.openPM();
		pm.currentTransaction().begin();

		Query q = pm.newQuery(TestClass.class, "_int < 12");
		Collection<?> c = (Collection<?>) q.execute();
		assertEquals(0, c.size());

		q = pm.newQuery(TestClass.class, "_int >= 12");
		c = (Collection<?>) q.execute();
		assertEquals(2, c.size());
		Iterator<?> it = c.iterator(); 
		assertEquals(oid1, pm.getObjectId(it.next()));
		assertEquals(oid2, pm.getObjectId(it.next()));
		
		TestTools.closePM();
	}
}
