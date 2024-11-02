package com.example.fastgamer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.metax.to.androidscreencaster.consts.ActivityServiceMessage
import com.metax.to.androidscreencaster.consts.ExtraIntent
import com.metax.to.androidscreencaster.service.ScreenCastService
import com.metax.to.androidscreencaster.service.ScreenCastService.BAD
import com.metax.to.androidscreencaster.service.ScreenCastService.GOOD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private var speed: Long = 300
    private var scouterClickProcess: Boolean = false
    private var blockGamePlay: Boolean = true
    private var coinChecker: Int = 0
    private var onSwipe: Boolean = false
    private var lastPosition: Int = 0
    private var screenXCenter: Float = 0F
    private var gamerPosition: Int = 0
    private var swiperService: Swiper? = null
    private var screenCastRequest: ActivityResultLauncher<Intent>? = null
    private var mediaProjectorManager: MediaProjectionManager? = null
    private var screenCastServiceMessenger: Messenger? = null
    private val messenger = Messenger(Handler { msg ->
        Log.i("FastGamer", "Handler got message : " + msg.what)
        false
    })

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == GOOD) {
                Log.d("FastGamer", "Received GOOD message from screenCastService ")
            }
            if (intent.action == BAD){
                Log.d("FastGamer", "Received BAD message from screenCastService")
            }
        }
    }

    private val screenCastServiceConnection = object : ServiceConnection {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i("FastGamer", "$name service is connected.")

            screenCastServiceMessenger = Messenger(service)
            val msg = Message.obtain(null, ActivityServiceMessage.CONNECTED)
            msg.replyTo = messenger

            try {
                screenCastServiceMessenger!!.send(msg)
            } catch (e: RemoteException) {
                Log.e("FastGamer", "Failed to send message due to:$e")
                e.printStackTrace()
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i("Com.example.FastGamer", "$name service is disconnected.")
            screenCastServiceMessenger = null
        }
    }

    private var screenCastMessenger: Messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 777) {
            val msg = msg.data.getString("checked")
            Log.d("FastGamer", "Received message : $msg")
            when (msg.toString()) {
                "BAD", "BAD_ORANGE", "BAD_RED" -> gamer(msg.toString())
                "NO_COIN" -> onNoCoin()
                "FIND_COIN" -> coinChecker = 0
                "1", "0", "-1" -> onPositionMessage(msg.toString())
                "MAIN_VIEW", "END_VIEW" -> startGame(msg.toString())
            }
        }

        false
    })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        screenXCenter = getScreenCenterX().toFloat()
        mediaProjectorManager = getSystemService(MediaProjectionManager::class.java)

        screenCastRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    MainScope().launch {
                        getScreenCastIntent(result)?.let {
                            startService(it)
                            bindService(it, screenCastServiceConnection, BIND_IMPORTANT)
                            launchSwipeService()
//                            runFirstRunApp()

                        }
                    }

                }

            }
        val buttonStart = findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            screenCastRequest?.launch(mediaProjectorManager!!.createScreenCaptureIntent())
        }
    }

    private fun onPositionMessage(msg: String) {
        Log.i("com.example.fastRunner.position.msg", "msg > $msg")
        when(msg) {
            "1" -> gamerPosition = 1
            "0" -> gamerPosition = 0
            "-1" -> gamerPosition = -1
        }
    }

    private fun launchSwipeService() {
        if (!isAccessibilityServiceEnabled()) {
            // Prompt user to enable Accessibility Service
            Log.i("FastGamer", "Accessibility service is not enabled-----------------------------")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            swiperService = getSwiperService()
            if (swiperService != null) {
                Log.i("FastGamer", "Swiper service is initialized")
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Swiper service is not initialized",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onNoCoin() {
        coinChecker += 1
        if (coinChecker == 7) {
            gamer("NO_COIN")
            coinChecker = 0
        }
    }

    private fun startGame(msg: String) {

        if (swiperService == null ) {
            Log.i("FastGamer", "Swiper service is null")
            swiperService = getSwiperService()
            return
        }
        val touchX: Float
        val touchY: Float
        val repeating: Int
        when(msg) {
            "MAIN_VIEW" -> {
                touchX = 560F
                touchY = 1494F
                repeating = 10
            }
            "END_VIEW" -> {
                touchX = 635F
                touchY = 304F
                repeating = 1
            }
            else -> return
        }
        blockGamePlay = true
        CoroutineScope(Dispatchers.Main).launch {
            repeat(repeating) {
                delay(100)
                swiperService?.click(touchX, touchY)
                delay(50)
                blockGamePlay = false
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            speed = 300
            delay(16000)
            while (speed >= 80) {
                delay(3500)
                speed -= 10
            }
        }
    }

    private fun runFirstRunApp() {
//        val packageName = "com.firstrun.socialgameing"
        val packageName = "com.google.android.youtube"
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scouter() {
        if (scouterClickProcess) return
        scouterClickProcess = true
        CoroutineScope(Dispatchers.Main).launch {
            MainScope().launch {
                Log.i("FastGamer", "Scouter process Started")
                while (scouterClickProcess) {
                    Log.i("FastGamer", "Scouter TEST---------------------------")
                    if (!blockGamePlay && swiperService != null) {
                        onSwipe = true
                        Log.i("FastGamer", "Scouter steep-----------============================================================================----------------")
                        swiperService?.click(342F, 1000F)
                        delay(30)
                        swiperService?.click(342F, 1000F)
                        delay(20)
                        onSwipe = false
                    }
                    delay(3000)
                }
            }
        }
    }



    private fun gamer(checkerMessage: String) {
        if(onSwipe || blockGamePlay) {
            Log.i("FastGamer", "PENDING__________________________________________///////// or blockGamePlay")
            return
        }
        if(checkerMessage != "NO_COIN") onSwipe = true
        Log.i("FastGamer", "Gamer called with $checkerMessage")
        if (swiperService == null ) {
            Log.i("FastGamer", "Swiper service is null")
            swiperService = getSwiperService()
            return
        }
        scouter()
        MainScope().launch {
            delay(speed)
            onSwipe = false
        }
        if (checkerMessage == "BAD_ORANGE" || checkerMessage == "BAD_RED") {
            MainScope().launch {
                delay(speed)
                swiperService?.swiping(screenXCenter, "top")
                Log.i(
                    "FastGamer",
                    "GAMER BAD_ORANGE or BAD_RED ----------------------------------0000000000000000000000000000000000000"
                )
            }
            return
        }
        when (gamerPosition) {
            -1 -> {
                swiperService?.swiping(screenXCenter,"right")
                gamerPosition = 0
                Log.i("FastGamer", "GAMER Left to center")
            }
            0 -> {
                if (lastPosition == 0) {
                    swiperService?.swiping(screenXCenter, "left")
                    lastPosition = 1
                    gamerPosition = 1
                } else {
                    swiperService?.swiping(screenXCenter, "right")
                    lastPosition = 0
                    gamerPosition = -1
                }
            }
            1 -> {
                Log.i("FastGamer", "GAMER right to center")
                swiperService?.swiping(screenXCenter,"left")
                gamerPosition = 0
            }
        }

    }

    private fun getScreenCenterX(): Int {
        val displayMetrics = this@MainActivity.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2
        return centerX
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLivestream()
        scouterClickProcess = false
        exitProcess(0)
    }

    private fun stopLivestream(){
        Log.i("FastGamer", "Stop Livestream")
        val msg = Message.obtain(null, ActivityServiceMessage.STOP)
        sendMessageToScreenCastService(msg)
        unbindService(screenCastServiceConnection)
    }

    private fun sendMessageToScreenCastService(msg: Message){

        if (screenCastServiceMessenger == null) {
            Log.w("FastGamer", "The Stop Stream msg not sent, because the messenger was null")
            return
        }
        msg.replyTo = messenger
        try {
            screenCastServiceMessenger!!.send(msg)
            Log.d("FastGamer", "sendMessageToScreenCastService function: send message $msg")
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ServiceCast")
    private fun getSwiperService(): Swiper? {
        val service = (application as App).swiperService
        if (service == null) {
            Log.e("FastGamer", "AccessibilityService is not available.")
            return null
        }

        Log.i("FastGamer", "Service: ${service.serviceInfo}")

        return if (service is Swiper) {
            service
        } else {
            Log.e("FastGamer", "Service is not an instance of Swiper.")
            null
        }
    }

    private fun getScreenCastIntent(requestResult: ActivityResult): Intent? {
       return try {
           val intent = Intent(this, ScreenCastService::class.java)
           intent.putExtra(ExtraIntent.RESULT_CODE.toString(), requestResult.resultCode)
           intent.putExtra(ExtraIntent.RESULT_DATA.toString(), requestResult.data)
           intent.putExtra(ExtraIntent.MESSENGER.toString(), screenCastMessenger)

        } catch (e: Exception) {
            Log.e("FastGamer", e.message.toString())
            null
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        // Get the component name for the Swiper service
        val componentName = ComponentName(this, Swiper::class.java).flattenToString()
        // Check if the enabled services contain the Swiper component name
        return enabledServices.split(":").contains(componentName)
    }


}