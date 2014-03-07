package org.zoodb.internal.server.index.btree;

import org.zoodb.internal.util.Pair;

public class BTreeNode {

    private final boolean isLeaf;
    private final int order;

    //ToDo maybe we want to have the keys set dynamically sized somehow
    private int numKeys;
    private long[] keys;

    private long[] values;
    private BTreeNode[] children;
    private BTreeNode parent;

    public BTreeNode(BTreeNode parent, int order, boolean isLeaf) {
        this.parent = parent;
        this.order = order;
        this.isLeaf = isLeaf;

        keys = new long[order - 1];
        numKeys = 0;

        if (isLeaf) {
            values = new long[order - 1];
        } else {
            children = new BTreeNode[order];
        }
    }

    /**
     * Returns the index + 1 of the key received as an argument. If the key is not in the array, it will return
     * the index of the smallest key in the array that is larger than the key received as argument.
     *
     * @param key
     * @return
     */
    public int findKeyPos(long key) {
        //ToDo change to binary search
        int i = 0;
        while (i < numKeys && key > keys[i]) {
            i++;
        }
        return i;
    }

    public BTreeNode findChild(long key) {
        return children[findKeyPos(key)];
    }

    /**
     * Leaf put.
     *
     * Requires that node is not full.
     * @param key
     * @param value
     */
    public void put(long key, long value) {
        if (!isLeaf()) {
            throw new UnsupportedOperationException("Should only be called on leaf nodes.");
        }

        int pos = findKeyPos(key);
        System.arraycopy(keys, pos, keys, pos + 1, numKeys - pos);
        System.arraycopy(values, pos, values, pos + 1, numKeys - pos);
        keys[pos] = key;
        values[pos] = value;
        numKeys++;
    }

    /**
     * Inner-node put.
     *
     * Requires that node is not full.
     * @param key
     * @param newNode
     */
    public void put(long key, BTreeNode newNode) {
        if (isLeaf()) {
            throw new UnsupportedOperationException("Should only be called on inner nodes.");
        }

        int pos = findKeyPos(key);
        System.arraycopy(children, pos, children, pos + 1, numKeys - pos + 1);
        children[pos] = newNode;

        System.arraycopy(keys, pos, keys, pos + 1, numKeys - pos);
        keys[pos] = key;
        numKeys++;
    }

    /**
     * Current only works for leaves.
     * @return
     */
    public BTreeNode split() {
        if (!isLeaf()) {
            throw new UnsupportedOperationException("Should only be called on leaf nodes.");
        }

        BTreeNode rightNode = new BTreeNode(parent, order, true);
        int keysPerNode = (int) Math.floor(order / 2);
        System.arraycopy(keys, keysPerNode, rightNode.getKeys(), 0, keysPerNode);
        System.arraycopy(values, keysPerNode, rightNode.getValues(), 0, keysPerNode);

        numKeys = keysPerNode;
        rightNode.setNumKeys(keysPerNode);
        rightNode.setParent(parent);

        return rightNode;
    }

    /**
     * Inserts a key and a new node to the inner structure of the tree.
     *
     * This methods is different from the split() method because when keys are insert on inner node,
     * the children pointers should also be shifted accordingly.
     *
     * @param key
     * @param newNode
     * @return
     */
    public Pair<BTreeNode, Long> insertAndSplit(long key, BTreeNode newNode) {
        if (isLeaf()) {
            throw new UnsupportedOperationException("Should only be called on inner nodes.");
        }

        //create a temporary node to allow the insertion
        BTreeNode tempNode = new BTreeNode(null, order + 1, false);
        System.arraycopy(keys, 0, tempNode.getKeys(), 0, numKeys);
        System.arraycopy(children, 0, tempNode.getChildren(), 0, order);
        tempNode.put(key, newNode);

        //split
        BTreeNode right = new BTreeNode(parent, order, false);
        int keysPerNode = (int) Math.floor((order - 1 )/ 2);
        //populate left node
        System.arraycopy(keys, 0, keys, 0, keysPerNode);
        System.arraycopy(children, 0, children, 0, keysPerNode + 1);
        numKeys = keysPerNode;

        //populate right node
        System.arraycopy(keys, keysPerNode + 1, right.getKeys(), 0, order - keysPerNode - 1);
        System.arraycopy(children, keysPerNode + 1, right.getChildren(), 0, order - keysPerNode);
        right.setNumKeys(order - keysPerNode - 1);

        //update children pointers
        for (int i = keysPerNode + 1; i < order + 1; i++) {
            tempNode.getChildren()[i].setParent(right);
        }
        right.setParent(parent);
        long keyToMoveUp = tempNode.getKeys()[keysPerNode];

        return new Pair<>(right, keyToMoveUp);
    }

    public void put(long key, BTreeNode left, BTreeNode right) {
        if (!isRoot()) {
            throw new UnsupportedOperationException("Should only be called on the root node.");
        }

        keys[0] = key;
        numKeys = 1;

        children[0] = left;
        children[1] = right;
    }

    private int getNumElements() {
        return numKeys;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public int getNumKeys() {
        return numKeys;
    }

    public BTreeNode getParent() {
        return parent;
    }

    public long[] getKeys() {
        return keys;
    }

    public long[] getValues() {
        return values;
    }

    public void setNumKeys(int numKeys) {
        this.numKeys = numKeys;
    }

    public long smallestKey() {
        return keys[0];
    }

    public long largestKey() {
        return keys[numKeys - 1];
    }

    public void setParent(BTreeNode parent) {
        this.parent = parent;
    }

    public BTreeNode[] getChildren() {
        return children;
    }

    public void setChildren(BTreeNode[] children) {
        this.children = children;
    }

    public void setKeys(long[] keys) {
        this.keys = keys;
    }


}