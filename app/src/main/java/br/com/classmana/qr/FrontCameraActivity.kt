package br.com.classmana.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FrontCameraActivity : ComponentActivity() {
    private val done = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 41, 76)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(12, 29, 57))
        previewView = PreviewView(this)
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding(18, 22, 18, 18)
            setBackgroundColor(Color.argb(205, 14, 34, 66))
        }
        val heading=TextView(this).apply {
            text="CLASSMANA  •  CHECK-IN"
            textSize=21f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
        }
        status=TextView(this).apply {
            text="Aproxime seu cartão da câmera frontal"
            textSize=15f; setTextColor(Color.WHITE);gravity=Gravity.CENTER
        }
        overlay.addView(heading)
        overlay.addView(status)
        val top=FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        root.addView(overlay,top)
        val cancel=Button(this).apply {
            text="Cancelar leitura";isAllCaps=false
            setOnClickListener { setResult(Activity.RESULT_CANCELED);finish() }
        }
        val cp=FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        cp.bottomMargin=28
        root.addView(cancel,cp)
        setContentView(root)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startFrontCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA),303)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==303){
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startFrontCamera()
            else { status.text="Permissão da câmera negada";setResult(Activity.RESULT_CANCELED) }
        }
    }

    private fun startFrontCamera(){
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            try{
                val provider=future.get()
                val camera=CameraSelector.DEFAULT_FRONT_CAMERA
                if(!provider.hasCamera(camera)){
                    status.text="Este dispositivo não possui câmera frontal disponível."
                    return@addListener
                }
                val preview=Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis=ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor){proxy->
                    val media=proxy.image
                    if(media==null||done.get()){proxy.close();return@setAnalyzer}
                    try {
                        val image=InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                val code=codes.firstOrNull { it.rawValue?.startsWith("CMQR1:")==true }
                                if(code!=null&&done.compareAndSet(false,true)){
                                    val result=Intent().putExtra("qr",code.rawValue)
                                    runOnUiThread { setResult(Activity.RESULT_OK,result);finish() }
                                }
                            }
                            .addOnCompleteListener {proxy.close()}
                    }catch(e:Exception){proxy.close()}
                }
                provider.unbindAll()
                provider.bindToLifecycle(this,camera,preview,analysis)
            }catch(e:Exception){status.text="Falha ao iniciar a câmera frontal"}
        },ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy(){
        super.onDestroy()
        scanner.close()
        executor.shutdown()
    }
}
