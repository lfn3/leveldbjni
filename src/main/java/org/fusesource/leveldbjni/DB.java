/*
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.leveldbjni;

import org.fusesource.hawtjni.runtime.*;
import org.w3c.dom.ranges.RangeException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static org.fusesource.hawtjni.runtime.FieldFlag.CONSTANT;
import static org.fusesource.hawtjni.runtime.MethodFlag.CONSTANT_INITIALIZER;
import static org.fusesource.hawtjni.runtime.MethodFlag.JNI;
import static org.fusesource.hawtjni.runtime.MethodFlag.POINTER_RETURN;

/**
 * The DB object provides the main interface to acessing LevelDB
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DB extends NativeObject {

    static final Library LIBRARY = new Library("leveldbjni", DB.class);

    @JniClass(name="leveldb::DB", flags={ClassFlag.CPP})
    static class DBJNI {
        static {
            DB.LIBRARY.load();
            init();
        }

        @JniMethod(flags={CONSTANT_INITIALIZER})
        private static final native void init();

        @JniField(flags={CONSTANT}, cast="JNIEnv *", accessor="env")
        static long ENV;

        @JniMethod(flags={JNI, POINTER_RETURN}, cast="jobject")
        public static final native long NewGlobalRef(
                Object target);

        @JniMethod(flags={JNI}, cast="jobject")
        public static final native void DeleteGlobalRef(
                @JniArg(cast="jobject", flags={ArgFlag.POINTER_ARG})
                long target);

        @JniMethod(flags={JNI, POINTER_RETURN}, cast="jclass")
        public static final native long GetObjectClass(
                Object target);

        @JniMethod(flags={JNI, POINTER_RETURN}, cast="jmethodID")
        public static final native long GetMethodID(
                @JniArg(cast="jclass", flags={ArgFlag.POINTER_ARG})
                long clazz,
                String name,
                String signature);

        @JniMethod(flags={MethodFlag.CPP_DELETE})
        static final native void delete(
                @JniArg(cast="leveldb::DB *") long self);

        @JniMethod(copy="leveldb::Status", accessor = "leveldb::DB::Open")
        static final native long Open(
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) Options options,
                @JniArg(cast="const char*") String path,
                @JniArg(cast="leveldb::DB**") long[] self);

        @JniMethod(copy="leveldb::Status", flags={MethodFlag.CPP})
        static final native long Put(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) WriteOptions options,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) Slice key,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) Slice value
                );

        @JniMethod(copy="leveldb::Status", flags={MethodFlag.CPP})
        static final native long Delete(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) WriteOptions options,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) Slice key
                );

        @JniMethod(copy="leveldb::Status", flags={MethodFlag.CPP})
        static final native long Write(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(flags={ArgFlag.BY_VALUE}) WriteOptions options,
                @JniArg(cast="leveldb::WriteBatch *") long updates
                );

        @JniMethod(copy="leveldb::Status", flags={MethodFlag.CPP})
        static final native long Get(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(flags={ArgFlag.NO_OUT, ArgFlag.BY_VALUE}) ReadOptions options,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) Slice key,
                @JniArg(cast="std::string *") long value
                );

        @JniMethod(cast="leveldb::Iterator *", flags={MethodFlag.CPP})
        static final native long NewIterator(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(flags={ArgFlag.NO_OUT, ArgFlag.BY_VALUE}) ReadOptions options
                );

        @JniMethod(cast="leveldb::Snapshot *", flags={MethodFlag.CPP})
        static final native long GetSnapshot(
                @JniArg(cast="leveldb::DB *") long self
                );

        @JniMethod(flags={MethodFlag.CPP})
        static final native void ReleaseSnapshot(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(cast="const leveldb::Snapshot *") long snapshot
                );

        @JniMethod(flags={MethodFlag.CPP})
        static final native void GetApproximateSizes(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(cast="const leveldb::Range *") long range,
                int n,
                @JniArg(cast="uint64_t*") long[] sizes
                );

        @JniMethod(flags={MethodFlag.CPP})
        static final native boolean GetProperty(
                @JniArg(cast="leveldb::DB *") long self,
                @JniArg(flags={ArgFlag.BY_VALUE, ArgFlag.NO_OUT}) Slice property,
                @JniArg(cast="std::string *") long value
                );

    }

    public void delete() {
        assertAllocated();
        DBJNI.delete(self);
        self = 0;
    }

    private DB(long self) {
        super(self);
    }

    public static class DBException extends IOException {
        private final boolean notFound;

        DBException(String s, boolean notFound) {
            super(s);
            this.notFound = notFound;
        }

        public boolean isNotFound() {
            return notFound;
        }
    }

    static void checkStatus(long s) throws DBException {
        Status status = new Status(s);
        try {
            if( !status.isOk() ) {
                throw new DBException(status.toString(), status.isNotFound());
            }
        } finally {
            status.delete();
        }
    }

    public static DB open(Options options, File path) throws IOException, DBException {
        long rc[] = new long[1];
        try {
            checkStatus(DBJNI.Open(options, path.getCanonicalPath(), rc));
        } catch (IOException e) {
            if( rc[0]!=0 ) {
                DBJNI.delete(rc[0]);
            }
            throw e;
        }
        return new DB(rc[0]);
    }

    public void put(WriteOptions options, byte[] key, byte[] value) throws DBException {
        NativeBuffer keyBuffer = new NativeBuffer(key);
        try {
            NativeBuffer valueBuffer = new NativeBuffer(value);
            try {
                put(options, keyBuffer, valueBuffer);
            } finally {
                valueBuffer.delete();
            }
        } finally {
            keyBuffer.delete();
        }
    }

    private void put(WriteOptions options, NativeBuffer keyBuffer, NativeBuffer valueBuffer) throws DBException {
        put(options, new Slice(keyBuffer), new Slice(valueBuffer));
    }

    private void put(WriteOptions options, Slice keySlice, Slice valueSlice) throws DBException {
        assertAllocated();
        checkStatus(DBJNI.Put(self, options, keySlice, valueSlice));
    }

    public void delete(WriteOptions options, byte[] key) throws DBException {
        NativeBuffer keyBuffer = new NativeBuffer(key);
        try {
            delete(options, keyBuffer);
        } finally {
            keyBuffer.delete();
        }
    }

    private void delete(WriteOptions options, NativeBuffer keyBuffer) throws DBException {
        delete(options, new Slice(keyBuffer));
    }

    private void delete(WriteOptions options, Slice keySlice) throws DBException {
        assertAllocated();
        checkStatus(DBJNI.Delete(self, options, keySlice));
    }

    public void write(WriteOptions options, WriteBatch updates) throws DBException {
        checkStatus(DBJNI.Write(self, options, updates.pointer()));
    }

    public byte[] get(ReadOptions options, byte[] key) throws DBException {
        NativeBuffer keyBuffer = new NativeBuffer(key);
        try {
            return get(options, keyBuffer);
        } finally {
            keyBuffer.delete();
        }
    }

    private byte[] get(ReadOptions options, NativeBuffer keyBuffer) throws DBException {
        return get(options, new Slice(keyBuffer));
    }

    private byte[] get(ReadOptions options, Slice keySlice) throws DBException {
        assertAllocated();
        StdString result = new StdString();
        try {
            checkStatus(DBJNI.Get(self, options, keySlice, result.pointer()));
            return result.toByteArray();
        } finally {
            result.delete();
        }
    }

    public Snapshot getSnapshot() {
        return new Snapshot(DBJNI.GetSnapshot(self));
    }

    public void releaseSnapshot(Snapshot snapshot) {
        DBJNI.ReleaseSnapshot(self, snapshot.pointer());
    }

    public Iterator iterator(ReadOptions options) {
        return new Iterator(DBJNI.NewIterator(self, options));
    }

    public static byte[] bytes(String value) {
        if( value == null) {
            return null;
        }
        try {
            return value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String asString(byte value[]) {
        if( value == null) {
            return null;
        }
        try {
            return new String(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public long[] getApproximateSizes(Range ... ranges) {
        if( ranges==null ) {
            return null;
        }

        long rc[] = new long[ranges.length];
        Range.RangeJNI structs[] = new Range.RangeJNI[ranges.length];
        if( rc.length> 0 ) {
            NativeBuffer range_array = Range.RangeJNI.arrayCreate(ranges.length);
            try {
                for(int i=0; i < ranges.length; i++) {
                    structs[i] = new Range.RangeJNI(ranges[i]);
                    structs[i].arrayWrite(range_array.pointer(), i);
                }
                DBJNI.GetApproximateSizes(self,range_array.pointer(), ranges.length, rc);
            } finally {
                for(int i=0; i < ranges.length; i++) {
                    if( structs[i] != null ) {
                        structs[i].delete();
                    }
                }
                range_array.delete();
            }
        }
        return rc;
    }

    public String getProperty(String name) throws DBException {
        NativeBuffer keyBuffer = new NativeBuffer(name.getBytes());
        try {
            byte[] property = getProperty(keyBuffer);
            if( property==null ) {
                return null;
            } else {
                return new String(property);
            }
        } finally {
            keyBuffer.delete();
        }
    }

    private byte[] getProperty(NativeBuffer nameBuffer) throws DBException {
        return getProperty(new Slice(nameBuffer));
    }

    private byte[] getProperty(Slice nameSlice) throws DBException {
        assertAllocated();
        StdString result = new StdString();
        try {
            if( DBJNI.GetProperty(self, nameSlice, result.pointer()) ) {
                return result.toByteArray();
            } else {
                return null;
            }
        } finally {
            result.delete();
        }
    }


}
