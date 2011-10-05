package org.zoodb.jdo.internal.server.index;

import java.lang.reflect.Field;
import java.util.NoSuchElementException;

import javax.jdo.JDOFatalDataStoreException;

import org.zoodb.jdo.internal.DataDeSerializer;
import org.zoodb.jdo.internal.Node;
import org.zoodb.jdo.internal.Util;
import org.zoodb.jdo.internal.ZooFieldDef;
import org.zoodb.jdo.internal.client.AbstractCache;
import org.zoodb.jdo.internal.client.CachedObject;
import org.zoodb.jdo.internal.server.DiskAccessOneFile;
import org.zoodb.jdo.internal.server.PagedObjectAccess;
import org.zoodb.jdo.internal.server.index.AbstractPagedIndex.AbstractPageIterator;
import org.zoodb.jdo.internal.server.index.AbstractPagedIndex.LongLongIndex;
import org.zoodb.jdo.internal.server.index.PagedUniqueLongLong.LLEntry;
import org.zoodb.jdo.spi.PersistenceCapableImpl;
import org.zoodb.jdo.stuff.CloseableIterator;
import org.zoodb.jdo.stuff.DatabaseLogger;

/**
 * TODO
 * This class can be improved in various ways:
 * a) Implement batch loading
 * b) Start a second thread that loads the next object after the previous one has been 
 *    delivered. 
 * c) Implement this iterator also in other reader classes.
 * 
 * @author Tilmann Z�schke
 */
public class ObjectIterator implements CloseableIterator<PersistenceCapableImpl> {

	private final LLIterator iter;  
	private final DiskAccessOneFile file;
	private final ZooFieldDef field;
	private final LongLongIndex index;
	private final DataDeSerializer deSer;
	private final boolean loadFromCache;
	private final AbstractCache cache;
	private PersistenceCapableImpl pc = null;
	
	/**
	 * Object iterator.
	 * 
	 * The last three fields can be null. If they are, the objects are simply returned and no checks
	 * are performed.
	 * 
	 * @param iter
	 * @param cache
	 * @param file
	 * @param clsDef Can be null.
	 * @param field Can be null.
	 * @param fieldInd Can be null.
	 */
	public ObjectIterator(AbstractPageIterator<LLEntry> iter, AbstractCache cache, 
			DiskAccessOneFile file, ZooFieldDef field, LongLongIndex fieldInd, 
			PagedObjectAccess in, Node node, boolean loadFromCache) {
		this.iter = (LLIterator) iter;
		this.file = file;
		this.field = field;
		this.index = fieldInd;
		this.deSer = new DataDeSerializer(in, cache, node);
		this.loadFromCache = loadFromCache; 
		this.cache = cache;
		findNext();
	}

	@Override
	public boolean hasNext() {
		return pc != null;
	}

	@Override
	public PersistenceCapableImpl next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		PersistenceCapableImpl ret = pc;
		findNext();
		return ret;
	}
	
	private void findNext() {
		LLEntry e;
		PersistenceCapableImpl pc;
		
		while (iter.hasNextULL()) {
			e = iter.nextULL();
			
			//try loading from cache first
			if (loadFromCache) {
	            long oid = e.getValue();
	            CachedObject co = cache.findCoByOID(oid);
	            if (co != null && !co.isStateHollow()) {
	                if (co.isDeleted()) {
	                    continue;
	                }
	                this.pc = co.obj;
	                return;
	                //TODO we also need to add objects that meet a query only because of local 
	                //updates to the object
	            }
	            //TODO small optimization: if this returns null, forward this to deserializer
	            //telling that cache-lok-up is pointless.
			}
			
			pc = file.readObject(deSer, e.getValue());
			//ignore if pc==null, because then object has been deleted
			if (pc != null && checkObject(e, pc)) {
				this.pc = pc;
				return;
			}
			// The elements can be outdated in normal indices because we do not directly remove entries
			// when they change, we remove them only when they are loaded and do not match anymore.
			// -> This is a problem when we rely on the index to get a count of matching objects.
			DatabaseLogger.debugPrintln(1, "Found outdated index entry for " + 
					Util.oidToString(e.getValue()));
			index.removeLong(e.getKey(), e.getValue());
		}
		close();
	}

	@Override
	public void remove() {
		// do we need this? Should we allow it? I guess it fails anyway in the LLE-iterator.
		iter.remove();
	}
	
	private boolean checkObject(LLEntry entry, PersistenceCapableImpl pc) {
		try {
			long val = entry.getKey();
			Field jField = field.getJavaField();
			if (field.isString()) {
				return val == BitTools.toSortableLong((String)jField.get(pc));
			}
			switch (field.getPrimitiveType()) {
			case BOOLEAN:
				return val == (jField.getBoolean(pc) ? 1 : 0);
			case BYTE: 
				return val == jField.getByte(pc);
			case DOUBLE: 
	    		System.out.println("STUB DiskAccessOneFile.writeObjects(DOUBLE)");
	    		//TODO
//				return entry.getValue() == jField.getDouble(pc);
	    		return false;
			case FLOAT:
				//TODO
	    		System.out.println("STUB DiskAccessOneFile.writeObjects(FLOAT)");
//				return entry.getValue() == jField.getFloat(pc);
	    		return false;
			case INT: 
				return val == jField.getInt(pc);
			case LONG: 
				return val == jField.getLong(pc);
			case SHORT: 
				return val == jField.getShort(pc);
			default:
				throw new IllegalArgumentException("type = " + field.getPrimitiveType());
			}
		} catch (SecurityException e) {
			throw new JDOFatalDataStoreException(
					"Error accessing field: " + field.getName(), e);
		} catch (IllegalArgumentException e) {
			throw new JDOFatalDataStoreException(
					"Error accessing field: " + field.getName(), e);
		} catch (IllegalAccessException e) {
			throw new JDOFatalDataStoreException(
					"Error accessing field: " + field.getName(), e);
		}
	}
	
	@Override
	public void close() {
		pc = null;
		iter.close();
	}
	
	//TODO remove?
//	@Override
//	protected void finalize() throws Throwable {
//		iter.close();
//		super.finalize();
//	}
}
