package com.incognito.vault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.incognito.vault.ui.camera.CameraScreen
import com.incognito.vault.ui.gallery.GalleryScreen
import com.incognito.vault.ui.lock.LockScreen
import com.incognito.vault.ui.navigation.Screen
import com.incognito.vault.ui.theme.IncognitoTheme
import com.incognito.vault.ui.viewer.MediaViewerScreen
import com.incognito.vault.viewmodel.VaultViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required for Incognito Vault", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ENFORCE WINDOW SECURITY FIRST: Prevent screenshots and task switcher previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        super.onCreate(savedInstanceState)

        // ProcessLifecycleOwner auto-lock registration
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                viewModel.lock()
            }
        })

        // Set callback for Application level auto-lock
        VaultApplication.onBackgroundLockRequested = {
            viewModel.lock()
        }

        // Handle incoming share intents (e.g. from WhatsApp, Gallery, etc.)
        handleIncomingShareIntent(intent)

        // Check & request permissions
        checkAndRequestPermissions()

        setContent {
            IncognitoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    VaultAppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Automatic security lock on activity stop
        viewModel.lock()
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type ?: return

        if (!type.startsWith("image/") && !type.startsWith("video/")) return

        val urisToImport = mutableListOf<Uri>()

        if (Intent.ACTION_SEND == action) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                urisToImport.add(streamUri)
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!streamUris.isNullOrEmpty()) {
                urisToImport.addAll(streamUris)
            }
        }

        if (urisToImport.isNotEmpty()) {
            if (viewModel.isLocked.value) {
                viewModel.addPendingImports(urisToImport)
            } else {
                viewModel.importMediaFromUris(this, urisToImport) { _ -> }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun VaultAppContent(viewModel: VaultViewModel) {
    val navController = rememberNavController()
    val isLocked by viewModel.isLocked.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Toast event listener
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Force navigation to Lock screen whenever app locks
    LaunchedEffect(isLocked) {
        if (isLocked) {
            navController.navigate(Screen.Lock.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Lock.route
        ) {
            composable(Screen.Lock.route) {
                LockScreen(
                    viewModel = viewModel,
                    onUnlocked = {
                        navController.navigate(Screen.Camera.route) {
                            popUpTo(Screen.Lock.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Camera.route) {
                CameraScreen(
                    viewModel = viewModel,
                    onNavigateToGallery = {
                        navController.navigate(Screen.Gallery.route)
                    },
                    onLockRequested = {
                        viewModel.lock()
                    }
                )
            }

            composable(Screen.Gallery.route) {
                GalleryScreen(
                    viewModel = viewModel,
                    onNavigateToCamera = {
                        navController.navigate(Screen.Camera.route) {
                            popUpTo(Screen.Gallery.route) { inclusive = true }
                        }
                    },
                    onNavigateToViewer = { itemId ->
                        navController.navigate(Screen.Viewer.createRoute(itemId))
                    },
                    onLockRequested = {
                        viewModel.lock()
                    }
                )
            }

            composable(
                route = Screen.Viewer.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                MediaViewerScreen(
                    itemId = itemId,
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
