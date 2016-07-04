package io.github.yangxlei.bjnetwork;

import android.util.Log;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by yanglei on 16/6/24.
 * 为每个  NetCall 建立一个与 Tag(Object) 的引用. 当 Tag 被 JVM 回收后,自动关闭 NetCall.
 *
 */
public class BJNetResourceManager {

    private Object mDefaultObject = new Object();

    private ReferenceQueue<Object> mReferenceQueue = new ReferenceQueue<>();
    private Map<Integer, ResourceReference> mResourceRefMap = new ConcurrentHashMap<>();
    private ResourceCheckThread mResourceCheckThread = new ResourceCheckThread();

    public BJNetResourceManager() {
       mResourceCheckThread.start();
    }

    public void release() {

    }

    public void addNetCall(Object tag, BJNetCall call) {
        int key = mDefaultObject.hashCode();
        if (tag != null) {
           key = tag.hashCode();
        }

        if (mResourceRefMap.containsKey(key)) {
            ResourceReference reference = mResourceRefMap.get(key);
            reference.add(call);
        } else {
            ResourceReference reference = new ResourceReference(tag == null ? mDefaultObject : tag, mReferenceQueue);
            reference.add(call);
            mResourceRefMap.put(key, reference);
        }
    }

    public void removeNetCall(Object tag, BJNetCall call) {
        int key = mDefaultObject.hashCode();
        if (tag != null) {
            key = tag.hashCode();
        }

        if (mResourceRefMap.containsKey(key)) {
            ResourceReference reference = mResourceRefMap.get(key);
            reference.remove(call);
        } else {

        }
    }

    public void removeAll(Object tag) {
        int key = mDefaultObject.hashCode();
        if (tag != null) {
            key = tag.hashCode();
        }

        if (mResourceRefMap.containsKey(key)) {
            ResourceReference reference = mResourceRefMap.get(key);
            reference.cancelAll();
            mResourceRefMap.remove(key);
        }
    }


    private static class ResourceReference extends PhantomReference<Object> {

        private int tagId;
        private String tagName;
        private List<BJNetCall> list;

        /**
         * Constructs a new phantom reference and registers it with the given
         * reference queue. The reference queue may be {@code null}, but this case
         * does not make any sense, since the reference will never be enqueued, and
         * the {@link #get()} method always returns {@code null}.
         *
         * @param r the referent to track
         * @param q the queue to register the phantom reference object with
         */
        public ResourceReference(Object r, ReferenceQueue<? super Object> q) {
            super(r, q);
            this.tagId = r.hashCode();
            this.tagName = r.getClass().getSimpleName();
            this.list = Collections.synchronizedList(new LinkedList<BJNetCall>());
        }

        public int getTagId() {
            return tagId;
        }

        public String getTagName() {
            return tagName;
        }

        public void add(BJNetCall call) {
            list.add(call);
        }

        public void remove(BJNetCall call) {
            list.remove(call);
        }

        public void cancelAll() {
            for (BJNetCall call : list) {
                try {
                    call.cancel();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            list.clear();
        }
    }

    private class ResourceCheckThread extends Thread {

        public ResourceCheckThread()  {
            super("NetResourceCheckThread");
            setPriority(Thread.MAX_PRIORITY);
            setDaemon(true);
        }


        @Override
        public void run() {

            ResourceReference reference = null;

            while (! interrupted()) {

                if (reference != null) {
                    reference.cancelAll();
                    reference.clear();
                    mResourceRefMap.remove(reference.getTagId());
                    Log.i("BJNetResource", "["+reference.getTagName() +"("+reference.getTagId()+")"+" is released and cancel calls auto.]");
                }

                // default tag 域中可能有已经执行完成了的 call. 将其回收掉
                ResourceReference defaultReference =  mResourceRefMap.get(mDefaultObject.hashCode());
                if (defaultReference != null) {
                    Iterator<BJNetCall> iterator = defaultReference.list.listIterator();
                    while (iterator.hasNext()) {
                        if (! iterator.next().isExecuted()) {
                            iterator.remove();
                            Log.i("BJNetResource", "["+defaultReference.getTagName() +"("+defaultReference.getTagId()+")"+" is cleanup and cancel calls auto.]");
                        }
                    }
                }

                try {
                    // remove() 会 wait 住线程
                    reference = (ResourceReference) mReferenceQueue.remove(1000);
                }  catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
