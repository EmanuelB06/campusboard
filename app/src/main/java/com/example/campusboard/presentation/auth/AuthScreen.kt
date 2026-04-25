package com.example.campusboard.presentation.auth

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    val gridColor = Color(0xFFE3F2FD) 
    Canvas(modifier = modifier.fillMaxSize().background(Color.White)) {
        val step = 30.dp.toPx()
        val width = size.width
        val height = size.height
        var y = 0f
        while (y < height) {
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
            y += step
        }
        var x = 0f
        while (x < width) {
            drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
            x += step
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state = viewModel.state.value
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var staySignedIn by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val communities = listOf("BSIT", "BSBA", "BEED", "BSSW")

    if (state.needsCommunitySelection) {
        CommunitySelectionDialog(
            communities = communities,
            onCommunitySelected = { viewModel.onEvent(AuthEvent.SelectCommunity(it)) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GridBackground()
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.isLoginMode) "Login" else "Register",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (state.isLoginMode) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    } else {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null)
                            }
                        }
                    )
                    
                    if (!state.isLoginMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(imageVector = image, contentDescription = null)
                                }
                            }
                        )
                    }

                    if (state.isLoginMode) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = staySignedIn, onCheckedChange = { staySignedIn = it })
                            Text("Stay signed in")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (state.isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                if (state.isLoginMode) viewModel.onEvent(AuthEvent.Login(email, password, staySignedIn))
                                else viewModel.onEvent(AuthEvent.Register(email, username, password, confirmPassword))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (state.isLoginMode) "Login" else "Register")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    handleGoogleAuth(context, viewModel, state.isLoginMode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("G", fontWeight = FontWeight.Bold, color = Color.Blue) 
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (state.isLoginMode) "Sign in with Google" else "Sign up with Google")
                            }
                        }
                        
                        TextButton(onClick = { viewModel.onEvent(AuthEvent.ToggleMode) }) {
                            Text(if (state.isLoginMode) "Don't have an account? Register" else "Already have an account? Login")
                        }
                    }
                    
                    state.error?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun CommunitySelectionDialog(
    communities: List<String>,
    onCommunitySelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { }, // Force selection
        title = { Text("Select Your Community") },
        text = {
            Column {
                Text("Please choose your campus department to continue.")
                Spacer(modifier = Modifier.height(16.dp))
                communities.forEach { community ->
                    OutlinedButton(
                        onClick = { onCommunitySelected(community) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(community)
                    }
                }
            }
        },
        confirmButton = { }
    )
}

private suspend fun handleGoogleAuth(
    context: Context, 
    viewModel: AuthViewModel, 
    isLoginMode: Boolean
) {
    val serverClientId = "576654187241-iph29tmauiqti0oeupuu5pqj7p7ctjt5.apps.googleusercontent.com"
    
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            if (isLoginMode) {
                viewModel.onEvent(AuthEvent.SignInWithGoogle(credential.idToken))
            } else {
                val googleName = credential.displayName ?: "Google User"
                viewModel.onEvent(AuthEvent.SignUpWithGoogle(credential.idToken, googleName))
            }
        }
    } catch (e: GetCredentialException) {
        viewModel.setError("Google Auth Error: ${e.message}")
    } catch (e: Exception) {
        viewModel.setError("An unexpected error occurred: ${e.message}")
    }
}
