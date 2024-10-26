package com.example.fastgamer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var screenCastRequest: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        screenCastRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
//                startLiveStream(result)
            } else {
//                changeLiveStreamUI(LS_FAILED)
//                Log.d(TAGSTREAM, "For ScreenCasting permission refused from user")
                // TODO: Need to handle the state of permission refused
            }
        }

        val buttonStart = findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                // Prompt user to enable Accessibility Service
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

            } else {
                val swiperService = getSwiperService()
                if (swiperService != null) {
                    MainScope().launch {
                        var value = 0
                        while (value < 30) {
                            swiperService.swiping("left")
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

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        // Get the component name for the Swiper service
        val componentName = ComponentName(this, Swiper::class.java).flattenToString()
        // Check if the enabled services contain the Swiper component name
        return enabledServices.split(":").contains(componentName)
    }


}