/**
 * 
 */
package org.zoodb.jdo.internal.client;

import javax.jdo.JDOUserException;
import javax.jdo.ObjectState;
import javax.jdo.PersistenceManager;
import javax.jdo.spi.Detachable;
import javax.jdo.spi.PersistenceCapable;
import javax.jdo.spi.StateManager;

import org.zoodb.jdo.internal.Node;
import org.zoodb.jdo.internal.Util;
import org.zoodb.jdo.internal.ZooClassDef;
import org.zoodb.jdo.spi.PersistenceCapableImpl;

public class CachedObject implements StateManager {
    
	private static final byte PS_PERSISTENT = 1;
	private static final byte PS_TRANSACTIONAL = 2;
	private static final byte PS_DIRTY = 4;
	private static final byte PS_NEW = 8;
	private static final byte PS_DELETED = 16;
	private static final byte PS_DETACHED = 32;

//	public enum STATE {
//		TRANSIENT, //optional JDO 2.2
//		//TRANSIENT_CLEAN, //optional JDO 2.2
//		//TRANSIENT_DIRTY, //optional JDO 2.2
//		PERSISTENT_NEW,
//		//PERSISTENT_NON_TRANS, //optional JDO 2.2
//		//PERSISTENT_NON_TRANS_DIRTY, //optional JDO 2.2
//		PERSISTENT_CLEAN,
//		PERSISTENT_DIRTY,
//		HOLLOW,
//		PERSISTENT_DELETED,
//		PERSISTENT_NEW_DELETED,
//		DETACHED_CLEAN,
//		DETACHED_DIRTY
//		;
//	}
	
	//TODO store only byte i.o. reference!
	private byte status;
	private byte stateFlags;
	
//	private final long oid;
	public final PersistenceCapableImpl obj;
	private final ClassNodeSessionBundle bundle;

	public static class CachedSchema extends CachedObject {
		public final ZooClassDef schema;
		public CachedSchema(ZooClassDef schema, ObjectState state) {
			super(schema, state, schema.getBundle());
			this.schema = schema;
		}
		/**
		 * 
		 * @return The ZooClassDef associated with this cached schema.
		 */
		public ZooClassDef getSchema() {
			return schema;
		}
	}
	
	public CachedObject(PersistenceCapableImpl pc, ObjectState state, 
			ClassNodeSessionBundle bundle) {
		this.bundle = bundle;
		if (bundle == null) {
			throw new RuntimeException(); //TODO remove
		}
		this.obj = pc;
		status = (byte)state.ordinal();
		switch (state) {
		case PERSISTENT_NEW: { 
			setPersNew();
			break;
		}
		case PERSISTENT_CLEAN: { 
			setPersClean();
			break;
		}
		case HOLLOW_PERSISTENT_NONTRANSACTIONAL: { 
			setHollow();
			break;
		}
		default:
			throw new UnsupportedOperationException("" + state);
		}
	}
	
	public boolean isDirty() {
		return (stateFlags & PS_DIRTY) != 0;
	}
	public boolean isNew() {
		return (stateFlags & PS_NEW) != 0;
	}
	public boolean isDeleted() {
		return (stateFlags & PS_DELETED) != 0;
	}
	public boolean isTransactional() {
		return (stateFlags & PS_TRANSACTIONAL) != 0;
	}
	public boolean isPersistent() {
		return (stateFlags & PS_PERSISTENT) != 0;
	}
	public final long getOID() {
		return obj.jdoZooGetOid();
		//return oid;
	}
	public final PersistenceCapableImpl getObject() {
		return obj;
	}
	public final Node getNode() {
		return bundle.getNode();
	}
	//not to be used from outside
	private void setPersNew() {
		status = (byte)ObjectState.PERSISTENT_NEW.ordinal();
		stateFlags = PS_PERSISTENT | PS_TRANSACTIONAL | PS_DIRTY | PS_NEW;
	}
	private void setPersClean() {
		status = (byte)ObjectState.PERSISTENT_CLEAN.ordinal();
		stateFlags = PS_PERSISTENT | PS_TRANSACTIONAL;
	}
	private void setPersDirty() {
		status = (byte)ObjectState.PERSISTENT_DIRTY.ordinal();
		stateFlags = PS_PERSISTENT | PS_TRANSACTIONAL | PS_DIRTY;
	}
	private void setHollow() {
		status = (byte)ObjectState.HOLLOW_PERSISTENT_NONTRANSACTIONAL.ordinal();
		stateFlags = PS_PERSISTENT;
	}
	private void setPersDeleted() {
		status = (byte)ObjectState.PERSISTENT_DELETED.ordinal();
		stateFlags = PS_PERSISTENT | PS_TRANSACTIONAL | PS_DIRTY | PS_DELETED;
	}
	private void setPersNewDeleted() {
		status = (byte)ObjectState.PERSISTENT_NEW_DELETED.ordinal();
		stateFlags = PS_PERSISTENT | PS_TRANSACTIONAL | PS_DIRTY | PS_NEW | PS_DELETED;
	}
	private void setDetachedClean() {
		status = (byte)ObjectState.DETACHED_CLEAN.ordinal();
		stateFlags = PS_DETACHED;
	}
	private void setDetachedDirty() {
		status = (byte)ObjectState.DETACHED_DIRTY.ordinal();
		stateFlags = PS_DETACHED | PS_DIRTY;
	}
	public void markClean() {
		//TODO is that all?
		setPersClean();
	}
	public void markDirty() {
		ObjectState statusO = ObjectState.values()[status];
		if (statusO == ObjectState.PERSISTENT_CLEAN) {
			setPersDirty();
		} else if (statusO == ObjectState.PERSISTENT_NEW) {
			//is already dirty
			//status = ObjectState.PERSISTENT_DIRTY;
		} else if (statusO == ObjectState.PERSISTENT_DIRTY) {
			//is already dirty
			//status = ObjectState.PERSISTENT_DIRTY;
		} else {
			throw new IllegalStateException("Illegal state transition: " + status + "->Dirty: " + 
					Util.oidToString(getOID()));
		}
	}
	public void markDeleted() {
		ObjectState statusO = ObjectState.values()[status];
		if (statusO == ObjectState.PERSISTENT_CLEAN ||
				statusO == ObjectState.PERSISTENT_DIRTY) {
			setPersDeleted();
		} else if (statusO == ObjectState.PERSISTENT_NEW) {
			setPersNewDeleted();
		} else if (statusO == ObjectState.HOLLOW_PERSISTENT_NONTRANSACTIONAL) {
			setPersDeleted();
		} else if (statusO == ObjectState.PERSISTENT_DELETED 
				|| statusO == ObjectState.PERSISTENT_NEW_DELETED) {
			throw new JDOUserException("The object has already been deleted: " + 
					Util.oidToString(getOID()));
		} else {
			throw new IllegalStateException("Illegal state transition: " + status + "->Deleted");
		}
	}
	public void markHollow() {
		//TODO is that all?
		setHollow();
	}

	public boolean isStateHollow() {
		return status == ObjectState.HOLLOW_PERSISTENT_NONTRANSACTIONAL.ordinal();
	}
	
	// *************************************************************************
	// StateManagerImpl
	// *************************************************************************
		
	//JDO 2.0

	@Override
	public boolean getBooleanField(PersistenceCapable arg0, int arg1,
			boolean arg2) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public byte getByteField(PersistenceCapable arg0, int arg1, byte arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public char getCharField(PersistenceCapable arg0, int arg1, char arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDoubleField(PersistenceCapable arg0, int arg1, double arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloatField(PersistenceCapable arg0, int arg1, float arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getIntField(PersistenceCapable arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLongField(PersistenceCapable arg0, int arg1, long arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Object getObjectField(PersistenceCapable arg0, int arg1, Object arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getObjectId(PersistenceCapable arg0) {
		return getOID();
		//TODO optimize
		//return ((PersistenceCapableImpl)arg0).jdoZooGetOid();
	}

	public long getObjectId(PersistenceCapableImpl arg0) {
		return getOID();
		//TODO optimize and use
		//return arg0.jdoZooGetOid();
	}

//	public void setObjectId(PersistenceCapable arg0, long oid) {
//		((PersistenceCapableImpl)arg0).jdoZooSetOid(oid);
//		this.oid = oid;
//	}

	@Override
	public PersistenceManager getPersistenceManager(PersistenceCapable arg0) {
		return bundle.getSession().getPersistenceManager();
	}

	@Override
	public short getShortField(PersistenceCapable arg0, int arg1, short arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getStringField(PersistenceCapable arg0, int arg1, String arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getTransactionalObjectId(PersistenceCapable arg0) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public Object getVersion(PersistenceCapable arg0) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public boolean isDeleted(PersistenceCapable arg0) {
		if (arg0 != obj) {
			throw new IllegalArgumentException();
		}
		return isDeleted();
	}

	@Override
	public boolean isDirty(PersistenceCapable arg0) {
		if (arg0 != obj) {
			throw new IllegalArgumentException();
		}
		return isDirty();
//		CachedObject co = _cache.findCO((PersistenceCapableImpl) arg0);
//		return co.isDirty();
//		//return ((PersistenceCapableImpl)arg0).jdoZooFlagIsSet(JDO_PC_DIRTY);
	}

	@Override
	public boolean isLoaded(PersistenceCapable arg0, int arg1) {
		if (arg0 != obj) {
			throw new IllegalArgumentException();
		}
		return !isStateHollow();
//		//TODO correct?
//		long oid = ((PersistenceCapableImpl) arg0).jdoZooGetOid();
//		CachedObject co = _cache.findCoByOID(oid);
////		if (co == null || co.isStateHollow()) {
////			
////		}
//		return !co.isStateHollow();
	}

	@Override
	public boolean isNew(PersistenceCapable arg0) {
		if (arg0 != obj) {
			throw new IllegalArgumentException();
		}
		return isNew();
//		CachedObject co = _cache.findCO((PersistenceCapableImpl) arg0);
//		return co.isNew();
//		//return ((PersistenceCapableImpl)arg0).jdoZooFlagIsSet(JDO_PC_NEW);
	}

	@Override
	public boolean isPersistent(PersistenceCapable arg0) {
		if (arg0 != obj) {
			throw new IllegalArgumentException();
		}
		//CachedObject co = _cache.findCO((PersistenceCapableImpl) arg0);
		//return co.isPDeleted();
		//TODO check for isDeleted? isDetached?
		return true; //If it has a STateManager, it must be persistent. //TODO not true!!! See isLoaded()
		//return ((PersistenceCapableImpl)arg0).jdoZooFlagIsSet(JDO_PC_PERSISTENT);
	}

	@Override
	public boolean isTransactional(PersistenceCapable arg0) {
		if (arg0 != obj) {
			throw new IllegalArgumentException();
		}
		return isTransactional();
//		CachedObject co = _cache.findCO((PersistenceCapableImpl) arg0);
//		return co.isTransactional();
//		//return ((PersistenceCapableImpl)arg0).jdoZooFlagIsSet(JDO_PC_TRANSACTIONAL);
	}

	@Override
	public void makeDirty(PersistenceCapable arg0, String arg1) {
		if (arg0 == obj) {
			markDirty();
			return;
		}
		throw new IllegalArgumentException();
//		CachedObject co = _cache.findCO((PersistenceCapableImpl) arg0);
//		// TODO Fix, update/use field information
//		co.markDirty();
//		//((PersistenceCapableImpl)arg0).jdoZooSetFlag(JDO_PC_DIRTY);
	}

	@Override
	public void preSerialize(PersistenceCapable arg0) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
		
	}

	@Override
	public void providedBooleanField(PersistenceCapable arg0, int arg1,
			boolean arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedByteField(PersistenceCapable arg0, int arg1, byte arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedCharField(PersistenceCapable arg0, int arg1, char arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedDoubleField(PersistenceCapable arg0, int arg1,
			double arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedFloatField(PersistenceCapable arg0, int arg1, float arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedIntField(PersistenceCapable arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedLongField(PersistenceCapable arg0, int arg1, long arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedObjectField(PersistenceCapable arg0, int arg1,
			Object arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedShortField(PersistenceCapable arg0, int arg1, short arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void providedStringField(PersistenceCapable arg0, int arg1,
			String arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean replacingBooleanField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public byte replacingByteField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public char replacingCharField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Object[] replacingDetachedState(Detachable arg0, Object[] arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double replacingDoubleField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public byte replacingFlags(PersistenceCapable arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float replacingFloatField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int replacingIntField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long replacingLongField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Object replacingObjectField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public short replacingShortField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public StateManager replacingStateManager(PersistenceCapable arg0,
			StateManager arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String replacingStringField(PersistenceCapable arg0, int arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setBooleanField(PersistenceCapable arg0, int arg1,
			boolean arg2, boolean arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setByteField(PersistenceCapable arg0, int arg1, byte arg2,
			byte arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setCharField(PersistenceCapable arg0, int arg1, char arg2,
			char arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDoubleField(PersistenceCapable arg0, int arg1, double arg2,
			double arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setFloatField(PersistenceCapable arg0, int arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setIntField(PersistenceCapable arg0, int arg1, int arg2,
			int arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLongField(PersistenceCapable arg0, int arg1, long arg2,
			long arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setObjectField(PersistenceCapable arg0, int arg1, Object arg2,
			Object arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setShortField(PersistenceCapable arg0, int arg1, short arg2,
			short arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setStringField(PersistenceCapable arg0, int arg1, String arg2,
			String arg3) {
		// TODO Auto-generated method stub
		
	}

	public boolean hasState(ObjectState state) {
		return this.status == state.ordinal();
	}

	public final ZooClassDef getClassDef() {
		return bundle.getClassDef();
	}
}

