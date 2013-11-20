/**
 * $Id$
 */
package com.untangle.jnetcap;

/* This is an abstract class because it should only be used as an inner class that gets a pointer
 * directly from C *
 * this was abstract, for the reason listed above, but not it package protected which should be
 * good enough */
class CPointer 
{
    public static final long NULL = 0;
    protected long pointer = NULL;
    
    CPointer( long pointer ) 
    {
        if ( pointer == NULL ) throw new NullPointerException();
        this.pointer = pointer;
    }
    
    long value() 
    {
        if ( pointer == NULL ) throw new NullPointerException();
        return pointer;
    }

    void raze() 
    {
        pointer = NULL;
    }
}
