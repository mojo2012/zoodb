package org.zoodb.internal.server.index.btree;

import org.zoodb.internal.util.Pair;

import java.util.Observable;

/**
 * Represents the node of a B+ tree.
 * 
 * Support for linked-lists of nodes on the leaf level is yet to be added.
 */
public abstract class BTreeNode extends Observable {

	private final boolean isLeaf;
	private boolean isRoot;
	protected final int order;

	// ToDo maybe we want to have the keys set dynamically sized somehow
	protected int numKeys;
	private long[] keys;

	private long[] values;

	public BTreeNode(int order, boolean isLeaf, boolean isRoot) {
		this.order = order;
		this.isLeaf = isLeaf;
		this.isRoot = isRoot;

		initializeEntries(order);
	}

    public abstract void initializeEntries(int order);

    public abstract BTreeNode newNode(int order, boolean isLeaf, boolean isRoot);
    public abstract boolean equalChildren(BTreeNode other);
    public abstract void copyChildren(BTreeNode source, int sourceIndex,
                                      BTreeNode dest, int destIndex, int size);
    protected abstract <T extends BTreeNode> T  leftSiblingOf(BTreeNode node);
    protected abstract <T extends BTreeNode> T  rightSiblingOf(BTreeNode node);
    public abstract BTreeNode getChild(int index);
    public abstract void setChild(int index, BTreeNode child);
    public abstract BTreeNode[] getChildren();
    public abstract void setChildren(BTreeNode[] children);
    public abstract void markChanged();

    // closes (destroys) node
    public abstract void close();
    /*
        Node modification operations
     */
    //TODO change this
    public abstract void migrateEntry(int destinationPos, BTreeNode source, int sourcePos);
    public abstract void setEntry(int pos, long key, long value);

    public abstract void copyFromNodeToNode(int srcStartK, int srcStartC, BTreeNode destination, int destStartK, int destStartC, int keys, int children);
    public abstract void shiftRecords(int startIndex, int endIndex, int amount);
    public abstract void shiftRecordsRight(int amount);
    public abstract void shiftRecordsLeftWithIndex(int startIndex, int amount);
    protected abstract boolean containsAtPosition(int position, long key, long value);
    protected abstract boolean smallerThanKeyValue(int position, long key, long value);
    protected abstract boolean checkIllegalInsert(int position, long key, long value);

    public void put(long key, long value) {
        if (!isLeaf()) {
            throw new IllegalStateException(
                    "Should only be called on leaf nodes.");
        }

        int pos = BTreeUtils.findKeyValuePos(this, key, value);
        if (checkIllegalInsert(pos, key, value)) {
            throw new IllegalStateException(
                    "Tree is not allowed to have non-unique values.");
        }
        shiftRecords(pos, pos + 1, getNumKeys() - pos);
        setKey(pos, key);
        setValue(pos, value);
        incrementNumKyes();

        //signal change
        markChanged();
    }

    public <T extends BTreeNode> T leftSibling(BTreeNode parent) {
        return (parent == null) ? null : (T) parent.leftSiblingOf(this);
    }

    public <T extends BTreeNode> T rightSibling(BTreeNode parent) {
        return (parent == null) ? null : (T) parent.rightSiblingOf(this);
    }

	public void setKey(int index, long key) {
		getKeys()[index] = key;

        //signal change
        markChanged();
	}

	public void setValue(int index, long value) {
		getValues()[index] = value;

        //signal change
        markChanged();
	}

	public int keyIndexOf(BTreeNode left, BTreeNode right) {
		for (int i = 0; i < getNumKeys(); i++) {
			if (getChild(i) == left && getChild(i + 1) == right) {
				return i;
			}
		}
		return -1;
	}

	public boolean isUnderfull() {
		if (isRoot()) {
			return getNumKeys() == 0;
		}
		if (isLeaf()) {
			return getNumKeys() < minKeysAmount();
		}
		return getNumKeys() + 1 < order / 2.0;
	}

	public boolean hasExtraKeys() {
		if (isRoot()) {
			return true;
		}
		return getNumKeys() - 1 >= minKeysAmount();
	}

	public boolean isOverflowing() {
		return getNumKeys() >= order;
	}

	public boolean incrementNumKyes() {
        markChanged();
		return increaseNumKeys(1);
	}

	public boolean decrementNumKeys() {
        markChanged();
		return decreaseNumKeys(1);
	}

	public boolean increaseNumKeys(int amount) {
        markChanged();
		int newNumKeys = getNumKeys() + amount;
		if (newNumKeys > getKeys().length) {
			return false;
		}
		setNumKeys(newNumKeys);
		return true;
	}

	public boolean decreaseNumKeys(int amount) {
        markChanged();
		int newNumKeys = getNumKeys() - amount;
		if (newNumKeys < 0) {
			return false;
		}
		setNumKeys(newNumKeys);
		return true;
	}

	protected void initKeys(int order) {
		setKeys(new long[order - 1]);
		setNumKeys(0);
	}

    protected void initValues(int order) {
		setValues(new long[order - 1]);
	}

    protected void initChildren(int order) {
		setChildren(new BTreeNode[order]);
	}

	public long getValue(int index) {
		return getValues()[index];
	}

	public long getKey(int index) {
		return getKeys()[index];
	}

	public double minKeysAmount() {
		return (order - 1) / 2.0D;
	}

	public boolean isLeaf() {
		return isLeaf;
	}

	public boolean isRoot() {
		return isRoot;
	}

	public long getSmallestKey() {
		return keys[0];
	}

	public long getLargestKey() {
		return keys[numKeys - 1];
	}

    public long getSmallestValue() {
        return (values != null) ? values[0] : -1;
    }

    public long getLargestValue() {
        return (values != null) ? values[numKeys -1] : -1;
    }

	public int getNumKeys() {
		return numKeys;
	}

	public long[] getKeys() {
		return keys;
	}

	public long[] getValues() {
		return values;
	}

	public void setNumKeys(int numKeys) {
        markChanged();
		this.numKeys = numKeys;
	}

	public void setKeys(long[] keys) {
        markChanged();
		this.keys = keys;
	}

	public void setValues(long[] values) {
        markChanged();
		this.values = values;
	}

	public int getOrder() {
		return order;
	}

	public void setIsRoot(boolean isRoot) {
        markChanged();
		this.isRoot = isRoot;
	}

    public Pair<Long, Long> getKeyValue(int position) {
        Long key = getKey(position);
        Long value = (getValues() != null) ? getValue(position) : -1;
        return new Pair<>(key, value);
    }

    /**
     * Perform binary search on the key array for a certain key/value pair.
     *
     *
     * @param key   The key received as an argument.
     * @param value The value received as argument. Not used for decisions for unique trees.

     */
    public Pair<Boolean, Integer> binarySearch(long key, long value) {
        int low = 0;
        int high = this.getNumKeys() - 1;
        int mid = 0;
        boolean found = false;
        while (!found && low <= high) {
            mid = low + (high - low) / 2;
            if (containsAtPosition(mid, key, value)) {
                found = true;
            } else {
                if (smallerThanKeyValue(mid, key, value)) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
        }
        return new Pair<>(found, mid);
    }

    protected void shiftRecordsLeft(int amount) {
        markChanged();
        shiftRecordsLeftWithIndex(0, amount);
    }

    public void shiftKeys(int startIndex, int endIndex, int amount) {
        markChanged();
        System.arraycopy(getKeys(), startIndex, getKeys(), endIndex, amount);
    }

    protected void shiftValues(int startIndex, int endIndex, int amount) {
        markChanged();
        System.arraycopy(getValues(), startIndex, getValues(), endIndex, amount);
    }

    public void shiftChildren(int startIndex, int endIndex, int amount) {
        markChanged();
        copyChildren(this, startIndex, this, endIndex, amount);
    }

    public String toString() {
        String ret = (isLeaf() ? "leaf" : "inner") + "-node: k:";
        ret += "[";
        for (int i = 0; i < this.getNumKeys(); i++) {
            ret += Long.toString(keys[i]);
            if (i != this.getNumKeys() - 1)
                ret += " ";
        }
        ret += "]";
        ret += ",   \tv:";
        ret += "[";
        for (int i = 0; i < this.getNumKeys(); i++) {
            ret += Long.toString(values[i]);
            if (i != this.getNumKeys() - 1)
                ret += " ";
        }
        ret += "]";

        if (!isLeaf()) {
            ret += "\n\tc:";
            if (this.getNumKeys() != 0) {
                for (int i = 0; i < this.getNumKeys() + 1; i++) {
                    String[] lines = this.getChild(i).toString()
                            .split("\r\n|\r|\n");
                    for (String l : lines) {
                        ret += "\n\t" + l;
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BTreeNode))
            return false;

        BTreeNode bTreeNode = (BTreeNode) o;

        if (isLeaf() != bTreeNode.isLeaf())
            return false;
        if (getNumKeys() != bTreeNode.getNumKeys())
            return false;
        if (order != bTreeNode.order)
            return false;
        if (!isLeaf() && !equalChildren(bTreeNode))
            return false;
        if (!arrayEquals(getKeys(), bTreeNode.getKeys(), getNumKeys()))
            return false;
        // checking for parent equality would result in infinite loop
        // if (parent != null ? !parent.equals(bTreeNode.parent) :
        // bTreeNode.parent != null) return false;
        if (!arrayEquals(getValues(), bTreeNode.getValues(), getNumKeys()))
            return false;

        return true;
    }

    protected <T> boolean arrayEquals(T[] first, T[] second, int size) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (first.length < size || second.length < size) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            if ((first[i] != second[i]) && (first[i] != null)
                    && (!first[i].equals(second[i]))) {
                return false;
            }
        }
        return true;
    }

    private boolean arrayEquals(long[] first, long[] second, int size) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (first.length < size || second.length < size) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            if (!(first[i] == second[i])) {
                return false;
            }
        }
        return true;
    }

}
