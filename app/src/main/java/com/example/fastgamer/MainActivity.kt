package com.example.fastgamer

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
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
            val filter = IntentFilter().apply {
                addAction(GOOD)
                addAction(BAD)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                this@MainActivity.registerReceiver(broadcastReceiver, filter, Service.RECEIVER_EXPORTED)
            } else {
                this@MainActivity.registerReceiver(broadcastReceiver, filter)
            }
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
            Log.i("FastGamer", "$name service is disconnected.")
            screenCastServiceMessenger = null
        }
    }

    private var screenCastMessenger: Messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 777) {
            val msg = msg.data.getString("checked")
            Log.d("FastGamer", "Received message : $msg")
        }
        if (msg.what == ScreenCastService.MESSAGE_HEADING) {
            val newHeading = msg.data.getString("head")
            Log.i("FastGamer", "Received message heading:  $newHeading")
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
        mediaProjectorManager = getSystemService(MediaProjectionManager::class.java)

        screenCastRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                MainScope().launch {
                    getScreenCastIntent(result)?.let {
                        startService(it)
                        bindService(it, screenCastServiceConnection, BIND_AUTO_CREATE)
                        Log.i(
                            "FastGamer",
                            "Screen cast intent:---------------------------------------------------------- $it"
                        )
                    }
                }

            }

        }
        val buttonStart = findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            screenCastRequest?.launch(mediaProjectorManager!!.createScreenCaptureIntent())
            if (!isAccessibilityServiceEnabled()) {
                // Prompt user to enable Accessibility Service
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

            } else {
                val swiperService = getSwiperService()
                if (swiperService != null) {
                    MainScope().launch {
                        var value = 0
                        while (value < 30) {
                            //swiperService.swiping("left")
                            value++
                            delay(1000)
                        }
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Swiper service is not initialized",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLivestream()
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