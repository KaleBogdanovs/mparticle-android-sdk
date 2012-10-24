package com.mparticle;

import static org.mockito.Mockito.*;

import android.test.AndroidTestCase;

public class SessionLifecycleTests extends AndroidTestCase {

    private MessageManager mMockMessageManager;
    private MParticleAPI mMParticleAPI;

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      mMockMessageManager = mock(MessageManager.class);
      mMParticleAPI = new MParticleAPI(getContext(),"test","secret", mMockMessageManager);
    }

    // start new session on on start() call if one was not running
    public void testSessionStartInitial() {
        mMParticleAPI.start();
        assertNotNull(mMParticleAPI.mSessionID);
        assertTrue(mMParticleAPI.mSessionStartTime > 0);
        verify(mMockMessageManager, times(1)).startSession(eq(mMParticleAPI.mSessionID), eq(mMParticleAPI.mSessionStartTime));
    }

    // do not start a new session if start() called with delay < timeout
    public void testSessionStartResume() throws InterruptedException {
        mMParticleAPI.setSessionTimeout(5000);
        mMParticleAPI.start();
        String sessionUUID = mMParticleAPI.mSessionID;
        long sessionStartTime = mMParticleAPI.mSessionStartTime;
        mMParticleAPI.stop();
        Thread.sleep(20);
        mMParticleAPI.start();
        assertSame(sessionUUID, mMParticleAPI.mSessionID);
        assertEquals(sessionStartTime, mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
    }

    // do start a new session if start() called with delay > timeout and also end last session
    public void testSessionStartRestart() throws InterruptedException {
        mMParticleAPI.setSessionTimeout(10);
        mMParticleAPI.start();
        String sessionUUID = mMParticleAPI.mSessionID;
        long sessionStartTime = mMParticleAPI.mSessionStartTime;
        Thread.sleep(20);
        mMParticleAPI.start();
        assertNotSame(sessionUUID, mMParticleAPI.mSessionID);
        assertTrue(sessionStartTime < mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(2)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), anyLong());
    }

    // start new session on on start() call if event logged on unstarted session
    public void testSessionStartOnEvent() {
        mMParticleAPI.logEvent("test");
        assertNotNull(mMParticleAPI.mSessionID);
        assertTrue(mMParticleAPI.mSessionStartTime>0);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
    }

    // do start a new session if events logged with delay > timeout and also end last session
    public void testSessionEventTimeout() throws InterruptedException {
        mMParticleAPI.setSessionTimeout(50);
        mMParticleAPI.start();
        mMParticleAPI.logEvent("test1");
        String sessionUUID = mMParticleAPI.mSessionID;
        long sessionStartTime = mMParticleAPI.mSessionStartTime;
        Thread.sleep(200);
        mMParticleAPI.logEvent("test2");
        assertNotSame(sessionUUID, mMParticleAPI.mSessionID);
        assertTrue(sessionStartTime < mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(2)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), anyLong());
    }

    // do not start a new session if events logged with delay < timeout
    public void testSessionEventDoNotTimeout() throws InterruptedException {
        mMParticleAPI.setSessionTimeout(5000);
        mMParticleAPI.start();
        Thread.sleep(5);
        mMParticleAPI.logEvent("test1");
        mMParticleAPI.logEvent("test2");
        mMParticleAPI.logEvent("test3");
        assertTrue(mMParticleAPI.mLastEventTime > mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, never()).stopSession(anyString(), anyLong());
    }

    // start a new session if newSession() called explicitly and also end last session
    public void testSessionNewSession() throws InterruptedException{
        mMParticleAPI.start();
        String sessionUUID = mMParticleAPI.mSessionID;
        long sessionStartTime = mMParticleAPI.mSessionStartTime;
        Thread.sleep(5);
        mMParticleAPI.newSession();
        assertNotSame(sessionUUID, mMParticleAPI.mSessionID);
        assertTrue(sessionStartTime < mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(2)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), anyLong());
    }

    // track a session stop event but do not end the session
    public void testSessionStop() {
        mMParticleAPI.start();
        String sessionUUID = mMParticleAPI.mSessionID;
        mMParticleAPI.stop();
        assertFalse(mMParticleAPI.mSessionStartTime == 0);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), anyLong());
        verify(mMockMessageManager, never()).endSession(anyString(), anyLong());
    }

    // do start a new session if endSession() called explicitly
    public void testSessionEndExplicit() {
        mMParticleAPI.start();
        String sessionUUID = mMParticleAPI.mSessionID;
        mMParticleAPI.endSession();
        assertTrue(mMParticleAPI.mSessionStartTime == 0);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), anyLong());
        verify(mMockMessageManager, times(1)).endSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).endSession(eq(sessionUUID), anyLong());
    }

    // check for a timeout situation that ends a session but does not start a new session
    public void testSessionTimeoutStandalone() throws InterruptedException {
        mMParticleAPI.setSessionTimeout(50);
        mMParticleAPI.start();
        mMParticleAPI.logEvent("test1");
        String sessionUUID = mMParticleAPI.mSessionID;
        long lastEventTime = mMParticleAPI.mLastEventTime;
        Thread.sleep(200);
        mMParticleAPI.checkSessionTimeout();
        assertTrue(0 == mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), eq(lastEventTime));
    }

    // check for a timeout situation that ends a session but does not start a new session
    public void testSessionTimeoutBackground() throws InterruptedException {
        mMParticleAPI.setSessionTimeout(50);
        mMParticleAPI.start();
        mMParticleAPI.logEvent("test1");
        Thread.sleep(10);
        mMParticleAPI.logEvent("test2");
        String sessionUUID = mMParticleAPI.mSessionID;
        long lastEventTime = mMParticleAPI.mLastEventTime;
        Thread.sleep(200);
        mMParticleAPI.checkSessionTimeout();
        assertTrue(0 == mMParticleAPI.mSessionStartTime);
        verify(mMockMessageManager, times(1)).startSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(anyString(), anyLong());
        verify(mMockMessageManager, times(1)).stopSession(eq(sessionUUID), eq(lastEventTime));
    }

}
