package com.untangle.uvm.webui.jabsorb;

import java.util.TimeZone;

import org.jabsorb.serializer.AbstractSerializer;
import org.jabsorb.serializer.MarshallException;
import org.jabsorb.serializer.ObjectMatch;
import org.jabsorb.serializer.SerializerState;
import org.jabsorb.serializer.UnmarshallException;

public class TimeZoneSerializer extends AbstractSerializer {
	/**
	 * Unique serialisation id.
	 */
	private final static long serialVersionUID = 2;

	/**
	 * Classes that this can serialise.
	 */
	private static Class[] _serializableClasses = new Class[] { TimeZone.class };

	/**
	 * Classes that this can serialise to.
	 */
	private static Class[] _JSONClasses = new Class[] { String.class };

	public Class[] getJSONClasses() {
		return _JSONClasses;
	}

	public Class[] getSerializableClasses() {
		return _serializableClasses;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jabsorb.serializer.Serializer#marshall(org.jabsorb.serializer.SerializerState,
	 *      java.lang.Object, java.lang.Object)
	 */
	public Object marshall(SerializerState state, Object p, Object o)
			throws MarshallException {
		
        if( o == null ) {
            return "";
        } else if (o instanceof TimeZone) {
			return ((TimeZone)o).getID();
		}
        
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jabsorb.serializer.Serializer#tryUnmarshall(org.jabsorb.serializer.SerializerState,
	 *      java.lang.Class, java.lang.Object)
	 */
	public ObjectMatch tryUnmarshall(SerializerState state, Class clazz,
			Object json) throws UnmarshallException {

		state.setSerialized(json, ObjectMatch.OKAY);
		return ObjectMatch.OKAY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jabsorb.serializer.Serializer#unmarshall(org.jabsorb.serializer.SerializerState,
	 *      java.lang.Class, java.lang.Object)
	 */
	public Object unmarshall(SerializerState state, Class clazz, Object json)
			throws UnmarshallException {
		Object returnValue = null;
		String val = json instanceof String ? (String) json : json.toString();
		try {
			returnValue = TimeZone.getTimeZone(val);
		} catch (Exception e) {
			throw new UnmarshallException("Invalid \"TimeZone\" specified:"
					+ val);
		}
		
	    if (returnValue == null) {
			throw new UnmarshallException("invalid class " + clazz);
		}
		state.setSerialized(json, returnValue);
		return returnValue;
		
	}

}
