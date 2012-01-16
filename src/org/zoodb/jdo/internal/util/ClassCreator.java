package org.zoodb.jdo.internal.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;

import org.zoodb.jdo.spi.PersistenceCapableImpl;

public class ClassCreator extends URLClassLoader {

	private static final ClassCreator SINGLETON = new ClassCreator(); 
	
	private static byte[] ba = new byte[1000]; 
	private static final byte[] BA = {
		-54, -2, -70, -66, 0, 0, 0, 50, 0, 16, 
		7, 0, 2, 1, 0, 11, 98, 99, 101, 47, 
		77, 121, 67, 108, 97, 115, 115, 7, 0, 4, 
		1, 0, 25, 98, 99, 101, 47, 80, 101, 114, 
		115, 105, 115, 116, 101, 110, 116, 67, 97, 112, 
		97, 98, 108, 101, 73, 109, 112, 108, 1, 0, 
		6, 60, 105, 110, 105, 116, 62, 1, 0, 3, 
		40, 41, 86, 1, 0, 4, 67, 111, 100, 101, 
		10, 0, 3, 0, 9, 12, 0, 5, 0, 6, 
		1, 0, 15, 76, 105, 110, 101, 78, 117, 109, 
		98, 101, 114, 84, 97, 98, 108, 101, 1, 0, 
		18, 76, 111, 99, 97, 108, 86, 97, 114, 105, 
		97, 98, 108, 101, 84, 97, 98, 108, 101, 1, 
		0, 4, 116, 104, 105, 115, 1, 0, 13, 76, 
		98, 99, 101, 47, 77, 121, 67, 108, 97, 115, 
		115, 59, 1, 0, 10, 83, 111, 117, 114, 99, 
		101, 70, 105, 108, 101, 1, 0, 12, 77, 121, 
		67, 108, 97, 115, 115, 46, 106, 97, 118, 97, 
		0, 33, 0, 1, 0, 3, 0, 0, 0, 0, 
		0, 1, 0, 1, 0, 5, 0, 6, 0, 1, 
		0, 7, 0, 0, 0, 47, 0, 1, 0, 1, 
		0, 0, 0, 5, 42, -73, 0, 8, -79, 0, 
		0, 0, 2, 0, 10, 0, 0, 0, 6, 0, 
		1, 0, 0, 0, 3, 0, 11, 0, 0, 0, 
		12, 0, 1, 0, 0, 0, 5, 0, 12, 0, 
		13, 0, 0, 0, 1, 0, 14, 0, 0, 0, 
		2, 0, 15
	};
	
	private final HashMap<String, Class<?>> map = 
		new HashMap<String, Class<?>>();
	
	private ClassCreator() {
		super(new URL[]{}, ClassCreator.class.getClassLoader());
	}

	/**
	 * @param name Class name.
	 */
	public static Class<?> createClass(String className) {
		Class<?> cls = SINGLETON.map.get(className);
		if (cls != null) {
			return cls;
		}
		
		int len = BA.length;
		System.arraycopy(BA, 0, ba, 0, len);
		
		String name = convertDots(className);
		String superName = convertDots(PersistenceCapableImpl.class.getName());
		
		String shortName = name.substring(name.lastIndexOf('/') + 1);
		shortName += ".java";

		len += replace(shortName, 166, 168, 0, 12);
		len += replace(name, 137, 140, 2, 11);
		len += replace(superName, 31, 33, 0, 25);
		len += replace(name, 14, 16, 0, 11);
		
		cls = SINGLETON.defineClass(name, ba, 0, len);
		
		SINGLETON.map.put(name, cls);
		
		return cls;
	}

	
	private static int replace(String newName, int posLen, int posName, 
			int deltaLen, int lenToReplace) {
		ba[posLen] = (byte) (newName.length() + deltaLen >> 8);
		ba[posLen+1] = (byte) (newName.length() + deltaLen);
		int toCopy = BA.length - posName - lenToReplace;
		System.arraycopy(ba, posName + lenToReplace, ba, posName + newName.length(), 
				toCopy);
		for (int i = 0; i < newName.length(); i++) {
			ba[posName+i] = (byte) newName.charAt(i); 
		}
		//return delta len
		int deltaSize = newName.length() - lenToReplace; 
		return deltaSize;
	}	

	private static final String convertDots(String name) {
		StringBuilder sb = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i); 
			if (c == '.') {
				sb.append('/');
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}