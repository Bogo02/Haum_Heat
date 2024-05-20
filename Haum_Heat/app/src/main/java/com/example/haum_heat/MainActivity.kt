package com.example.haum_heat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import com.example.haum_heat.ui.theme.Haum_heatTheme
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var signInIntent: Intent
    private var navController: NavController? = null
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        navController?.let {
            onSignInResult(it, res)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Haum_heatTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val localNavController = rememberNavController()
                    navController = localNavController
                    NavHost(navController = localNavController, startDestination = "login") {
                        composable("login") {
                            LoginScreen { startSignIn() }
                        }
                        composable("mainPage") {
                            MainScreen(localNavController, context = applicationContext)
                        }
                    }
                }
            }
        }

        // Subscribe to FCM topic
        FirebaseMessaging.getInstance().subscribeToTopic("temperatureUpdates")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("MainActivity", "Subscribed to temperatureUpdates topic")
                } else {
                    Log.e("MainActivity", "Subscription failed", task.exception)
                }
            }
    }

    private fun startSignIn() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()

        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(navController: NavController, result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            navController.navigate("mainPage")
        } else {
            println(result.resultCode)
        }
    }
}

@Composable
fun LoginScreen(startSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.bulbok),
            contentDescription = "Your Image Description",
            contentScale = ContentScale.FillWidth
        )

        Spacer(modifier = Modifier.height(100.dp))

        Image(
            painter = painterResource(id = R.drawable.googlelogin),
            contentDescription = "Your Image Description",
            modifier = Modifier
                .clickable { startSignIn() }
                .size(width = 300.dp, height = 75.dp)
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun UserAvatar(onDisconnectClick: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    val photoUrl = user?.photoUrl

    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.padding(16.dp)
    ) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            content = {
                Image(
                    painter = rememberImagePainter(photoUrl),
                    contentDescription = "User Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        )

        Spacer(modifier = Modifier.height(15.dp))

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    onDisconnectClick()
                },
                text = { Text("Notifications", fontSize = 18.sp, style = TextStyle(fontWeight = FontWeight.Bold)) }
            )
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    onDisconnectClick()
                },
                text = { Text("Disconnect", fontSize = 18.sp) }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(navController: NavController, context: Context) {
    var isFanOn by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isFanOn) "Press to turn fan off" else "Press to turn fan on",
            modifier = Modifier.padding(25.dp),
            fontSize = 27.5.sp
        )
        Image(
            painter = painterResource(id = if (isFanOn) R.drawable.fanon else R.drawable.fanoff),
            contentDescription = "Fan Image",
            modifier = Modifier
                .clickable {
                    if (isFanOn) {
                        val elapsedTime = System.currentTimeMillis() - startTime
                        writeTimeToFile(context, elapsedTime / 1000)
                    } else {
                        startTime = System.currentTimeMillis()
                    }
                    isFanOn = !isFanOn
                    // Add code here to send command to turn on/off the fan in Raspberry Pi
                }
                .size(width = 300.dp, height = 300.dp)
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        UserAvatar(onDisconnectClick = {
            FirebaseAuth.getInstance().signOut()
            navController.navigate("login")
        })
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun writeTimeToFile(context: Context, timeInSeconds: Long) {
    try {
        val fanTimeFile = File(context.getExternalFilesDir(null), "fanOnTime.txt")
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDateTime = currentDateTime.format(formatter)
        val fileWriter = FileWriter(fanTimeFile, true) // Append mode
        fileWriter.appendLine("$formattedDateTime - Time On: $timeInSeconds seconds")
        fileWriter.close()

        // Upload file to Firebase Cloud Storage
        uploadFileToFirebaseStorage(fanTimeFile)

        Toast.makeText(context, "Data written and uploaded to Firebase Storage", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, "Error writing to file", Toast.LENGTH_SHORT).show()
    }
}

private fun uploadFileToFirebaseStorage(file: File) {
    val storage = Firebase.storage
    val storageRef = storage.getReferenceFromUrl("gs://iot-light-68724.appspot.com/")
    val fileUri = file.toUri()
    val fileReference = storageRef.child("fanOnTime.txt")

    fileReference.putFile(fileUri)
        .addOnSuccessListener {
            // File uploaded successfully
        }
        .addOnFailureListener { exception ->
            exception.printStackTrace()
        }
}
