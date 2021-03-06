/*
 * Copyright 2009-2016 Tilmann Zaeschke. All rights reserved.
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
package org.zoodb.internal.util;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class PrimLongSetZ implements PrimLongSet, Iterable<Long> {

	public static class Entry {
		final long key;
		Entry next = null;
		Entry(long key) {
			this.key = key;
		}
		public long getKey() {
			return key;
		}
	}
	
	private static final double LOAD_FACTOR = 0.75;
	
	private Entry[] entries;
	
	private int capacityPower = 6;
	//capacity = 2^capacityPower
	private int limitMax = (int) ((1 << capacityPower) * LOAD_FACTOR);
	private int size = 0;
	
	private int modCount = 0;

	private EntrySet entryResult;
	
	
	public PrimLongSetZ() {
		this(60);
	}
	
	
	public PrimLongSetZ(int capacity) {
		capacityPower = 1;
		while (1 << capacityPower < capacity) {
			capacityPower++;
		}
		int capacity2 = 1 << capacityPower;
		limitMax = (int) (capacity2 * LOAD_FACTOR);
		entries = new Entry[capacity2];
	}
	
	
	private int calcHash(long key) {
		//This works well with primes:
		//return (int) key % capacity;
		//Knuths multiplicative hash function
		return (int)(key * 2654435761L) >>> (32 - capacityPower);
	}
	
	
	private void checkRehash(int newSize) {
		if (newSize > limitMax && capacityPower < 31) {
			capacityPower++;
			int capacity = 1 << capacityPower;
			limitMax = (int) (capacity * LOAD_FACTOR);
			Entry[] oldEntries = entries;
			entries = new Entry[capacity];
			for (int i = 0; i < oldEntries.length; i++) {
				Entry e = oldEntries[i];
				while (e != null) {
					Entry eNext = e.next;
					e.next = null;
					putEntryNoCheck(e);
					e = eNext;
				}
			}
		}
	}
	
//	private void printHisto() {
//		int[] histo = new int[size];
//		for (int i = 0; i < entries.length; i++) {
//			int n = 0;
//			Entry<T> e = entries[i];
//			while (e != null) {
//				n++;
//				e = e.next;
//			}
//			histo[n]++;
//		}
//		System.out.println("Histo: " + Arrays.toString(histo));
//	}
	
	private void putEntryNoCheck(Entry e) {
		int pos = calcHash(e.key);
		e.next = entries[pos];
		entries[pos] = e;  
	}
	
	@Override
	public boolean add(long keyBits) {
		int pos = calcHash(keyBits);
		Entry e = entries[pos];
		while (e != null && e.key != keyBits) {
			e = e.next;
		}
		if (e != null) {
			return false;
		}
		modCount++;
		checkRehash(size + 1);
		putEntryNoCheck(new Entry(keyBits));
		size++;
		return true;
	}

	@Override
	public boolean remove(long keyBits) {
		int pos = calcHash(keyBits);
		Entry e = entries[pos];
		Entry prev = null;
		while (e != null && e.key != keyBits) {
			prev = e;
			e = e.next;
		}
		if (e != null) {
			//remove
			modCount++;
			size--;
			if (prev != null) {
				prev.next = e.next;
			} else {
				entries[pos] = e.next;
			}
			return true;
		}
		return false;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i < entries.length; i++) {
			entries[i] = null;
		}
		size = 0;
		modCount++;
	}

	@Override
	public boolean contains(long keyBits) {
		int pos = calcHash(keyBits);
		Entry e = entries[pos];
		while (e != null && e.key != keyBits) {
			e = e.next;
		}
		return e != null;
	}

	@Override
	public void addAll(PrimLongSet set) {
		for (Entry e : set.entries()) {
			add(e.getKey());
		}
	}

	@Override
	public Iterator<Long> iterator() {
		return new KeyIterator();
	}
	
	@Override
	public Set<Entry> entries() {
		if (entryResult == null) {
			entryResult = new EntrySet();
		}
		return entryResult;
	}

	private class EntrySet extends ResultSet<Entry> {

		@Override
		public Iterator<Entry> iterator() {
			return new EntryIterator();
		}
	}
	
	class EntryIterator implements Iterator<Entry> {
		private int pos = -1;
		private Entry next;
		private int currentModCount;
		public EntryIterator() {
			currentModCount = modCount;
			while (++pos < entries.length) {
				if (entries[pos] != null) {
					next = entries[pos];
					break;
				}
			}
		}
		
		private void findNext() {
			if (next.next != null) {
				next = next.next;
				return;
			}
			
			while (++pos < entries.length) {
				if (entries[pos] != null) {
					next = entries[pos];
					return;
				}
			}
			next = null;
		}
		
		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public Entry next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			if (currentModCount != modCount) {
				throw new ConcurrentModificationException();
			}
			Entry t = next;
			findNext();
			return t;
		}
	}


	class KeyIterator implements Iterator<Long> {
		private int pos = -1;
		private Entry next;
		private int currentModCount;
		public KeyIterator() {
			currentModCount = modCount;
			while (++pos < entries.length) {
				if (entries[pos] != null) {
					next = entries[pos];
					break;
				}
			}
		}
		
		private void findNext() {
			if (next.next != null) {
				next = next.next;
				return;
			}
			
			while (++pos < entries.length) {
				if (entries[pos] != null) {
					next = entries[pos];
					return;
				}
			}
			next = null;
		}
		
		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public Long next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			if (currentModCount != modCount) {
				throw new ConcurrentModificationException();
			}
			Entry t = next;
			findNext();
			return t.key;
		}
	}


	private abstract class ResultSet<R> implements Set<R> {
		@Override
		public int size() {
			return PrimLongSetZ.this.size();
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public boolean contains(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object[] toArray() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <R2> R2[] toArray(R2[] a) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean add(R e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends R> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
		
	}


	public boolean isEmpty() {
		return size == 0;
	}
	
}
