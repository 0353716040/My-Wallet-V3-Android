package piuk.blockchain.androidcore.data.access

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.blockchain.logging.CrashLogger

import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.PersistentPrefs

interface AccessState {

    var canAutoLogout: Boolean

    val pin: String

    var isLoggedIn: Boolean

    var isNewlyCreated: Boolean

    fun startLogoutTimer()

    fun setLogoutActivity(logoutActivity: Class<*>)

    fun stopLogoutTimer()

    fun logout()

    fun logIn()

    fun unpairWallet()

    fun forgetWallet()

    fun clearPin()
    fun setPin(pin: String)

    companion object {
        const val LOGOUT_ACTION = "info.blockchain.wallet.LOGOUT"
        const val PIN_LENGTH = 4
    }
}

fun String.isValidPin(): Boolean = (this != "0000" && this.length == AccessState.PIN_LENGTH)

internal class AccessStateImpl(
    val context: Context,
    val prefs: PersistentPrefs,
    val rxBus: RxBus,
    private val crashLogger: CrashLogger
) : AccessState {

    override var canAutoLogout = true

    private var logoutActivity: Class<*>? = null
    private var logoutPendingIntent: PendingIntent? = null

    private var thePin: String = ""
    override val pin: String
        get() = thePin

    override fun clearPin() {
        thePin = ""
    }

    override fun setPin(pin: String) {
        if (!pin.isValidPin()) {
            IllegalArgumentException("setting invalid pin!").let {
                crashLogger.logException(it)
                throw it
            }
        }
        thePin = pin
    }

    override var isLoggedIn = false
        set(loggedIn) {
            logIn()
            field = loggedIn
            if (this.isLoggedIn) {
                rxBus.emitEvent(AuthEvent::class.java, AuthEvent.LOGIN)
            } else {
                rxBus.emitEvent(AuthEvent::class.java, AuthEvent.LOGOUT)
            }
        }

    override var isNewlyCreated: Boolean
        get() = prefs.getValue(PersistentPrefs.KEY_NEWLY_CREATED_WALLET, false)
        set(newlyCreated) = prefs.setValue(PersistentPrefs.KEY_NEWLY_CREATED_WALLET, newlyCreated)

    /**
     * Called from BaseAuthActivity#onPause()
     */
    override fun startLogoutTimer() {
        if (canAutoLogout) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + LOGOUT_TIMEOUT_MILLIS,
                logoutPendingIntent
            )
        }
    }

    override fun setLogoutActivity(logoutActivity: Class<*>) {
        this.logoutActivity = logoutActivity

        val intent = Intent(context, logoutActivity)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.action = AccessState.LOGOUT_ACTION
        logoutPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
    }

    /**
     * Called from BaseAuthActivity#onResume()
     */
    override fun stopLogoutTimer() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(logoutPendingIntent)
    }

    override fun logout() {
        crashLogger.logEvent("logout. resetting pin")
        clearPin()
        val intent = Intent(context, logoutActivity!!)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.action = AccessState.LOGOUT_ACTION
        context.startActivity(intent)
    }

    override fun logIn() = prefs.logIn()

    override fun unpairWallet() {
        crashLogger.logEvent("unpair. resetting pin")
        clearPin()
        prefs.logOut()
        rxBus.emitEvent(AuthEvent::class.java, AuthEvent.UNPAIR)
    }

    override fun forgetWallet() = rxBus.emitEvent(AuthEvent::class.java, AuthEvent.FORGET)

    companion object {
        private const val LOGOUT_TIMEOUT_MILLIS = 1000L * 30L
    }
}