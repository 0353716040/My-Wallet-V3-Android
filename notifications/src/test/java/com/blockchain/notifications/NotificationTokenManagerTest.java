package com.blockchain.notifications;

import com.blockchain.logging.CrashLogger;
import com.blockchain.preferences.NotificationPrefs;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.payload.data.Wallet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import piuk.blockchain.android.testutils.RxTest;
import piuk.blockchain.androidcore.data.rxjava.RxBus;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class NotificationTokenManagerTest extends RxTest {

    private NotificationTokenManager subject;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PayloadManager payloadManager;
    @Mock
    private NotificationPrefs prefs;
    @Mock
    private RxBus rxBus;
    @Mock
    private NotificationTokenProvider notificationTokenProvider;
    @Mock
    private CrashLogger crashLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        subject = new NotificationTokenManager(
            notificationService,
            payloadManager,
            prefs,
            notificationTokenProvider,
            rxBus,
            crashLogger
        );
    }

    @Test
    public void storeAndUpdateToken_disabledNotifications() {
        // Arrange
        when(prefs.getArePushNotificationsEnabled()).thenReturn(false);
        when(payloadManager.getPayload()).thenReturn(null);

        // Act
        subject.storeAndUpdateToken("token");
        // Assert
        verify(prefs).getArePushNotificationsEnabled();
        verify(prefs).setFirebaseToken("token");
        verifyNoMoreInteractions(prefs);
    }

    @Test
    public void storeAndUpdateToken_enabledNotifications_notSignedIn() {
        // Arrange
        when(prefs.getArePushNotificationsEnabled()).thenReturn(true);
        when(payloadManager.getPayload()).thenReturn(null);

        // Act
        subject.storeAndUpdateToken("token");
        // Assert
        verify(prefs).getArePushNotificationsEnabled();
        verify(prefs).setFirebaseToken("token");
        verifyNoMoreInteractions(prefs);
    }

    @Test
    public void storeAndUpdateToken_enabledNotifications_signedIn() {
        // Arrange
        when(prefs.getArePushNotificationsEnabled()).thenReturn(true);

        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getGuid()).thenReturn("guid");
        when(mockPayload.getSharedKey()).thenReturn("sharedKey");
        when(payloadManager.getPayload()).thenReturn(mockPayload);

        when(notificationService.sendNotificationToken(anyString(), anyString(), anyString())).thenReturn(Completable.complete());

        // Act
        subject.storeAndUpdateToken("token");
        // Assert
        verify(prefs).getArePushNotificationsEnabled();
        verify(prefs).setFirebaseToken("token");
        verify(notificationService).sendNotificationToken("token", "guid", "sharedKey");
        verifyNoMoreInteractions(prefs);
    }

    @Test
    public void enableNotifications_requestToken() {
        // Arrange
        when(prefs.getFirebaseToken()).thenReturn("");
        when(notificationTokenProvider.notificationToken()).thenReturn(Single.just("token"));

        // Act
        subject.enableNotifications();
        // Assert
        verify(prefs).setArePushNotificationsEnabled(true);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    public void enableNotifications_storedToken() {
        // Arrange
        when(prefs.getFirebaseToken()).thenReturn("token");
        when(prefs.getArePushNotificationsEnabled()).thenReturn(true);

        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getGuid()).thenReturn("guid");
        when(mockPayload.getSharedKey()).thenReturn("sharedKey");
        when(payloadManager.getPayload()).thenReturn(mockPayload);

        when(notificationService.sendNotificationToken(anyString(), anyString(), anyString())).thenReturn(Completable.complete());

        // Act
        TestObserver<Void> testObservable = subject.enableNotifications().test();
        // Assert
        testObservable.assertComplete();
        testObservable.assertNoErrors();
        verify(prefs).setArePushNotificationsEnabled(true);
        verify(payloadManager, atLeastOnce()).getPayload();
        verify(notificationService).sendNotificationToken(anyString(), anyString(), anyString());
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    public void disableNotifications() throws Exception {
        // Arrange
        when(prefs.getFirebaseToken()).thenReturn("");

        // Act
        TestObserver<Void> testObservable = subject.disableNotifications().test();
        // Assert
        testObservable.assertComplete();
        testObservable.assertNoErrors();
        verify(notificationTokenProvider).deleteToken();
        verify(prefs).setArePushNotificationsEnabled(false);
        verifyNoMoreInteractions(notificationService);
    }
}