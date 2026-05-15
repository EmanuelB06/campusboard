package com.example.campusboard.presentation.auth

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.sp
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
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
        var x = 0f
        while (x < width) {
            drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1.dp.toPx())
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
    var staySignedIn by remember { mutableStateOf(state.staySignedIn) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val communities = listOf("BSIT", "BSBA", "BEED", "BSSW")

    if (state.needsCommunitySelection) {
        CommunitySelectionDialog(
            communities = communities,
            onCommunitySelected = { viewModel.onEvent(AuthEvent.SelectCommunity(it)) }
        )
    }

    if (state.showGoogleSignUpDialog) {
        GoogleSignUpPasswordDialog(
            onDismiss = { viewModel.onEvent(AuthEvent.CancelGoogleSignUp) },
            onConfirm = { password ->
                state.googleIdToken?.let { idToken ->
                    viewModel.onEvent(AuthEvent.SignUpWithGoogle(
                        idToken = idToken,
                        username = state.pendingGoogleUsername ?: "Google User",
                        password = password,
                        staySignedIn = state.staySignedIn
                    ))
                }
            }
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
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                        text = if (state.isLoginMode) "Login" else "Register",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0D47A1)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (state.isLoginMode) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", fontWeight = FontWeight.Medium) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF0D47A1),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFF0D47A1),
                                unfocusedLabelColor = Color(0xFF64748B),
                                cursorColor = Color(0xFF0D47A1)
                            )
                        )
                    } else {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username", fontWeight = FontWeight.Medium) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF0D47A1),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFF0D47A1),
                                unfocusedLabelColor = Color(0xFF64748B),
                                cursorColor = Color(0xFF0D47A1)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", fontWeight = FontWeight.Medium) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF0D47A1),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFF0D47A1),
                                unfocusedLabelColor = Color(0xFF64748B),
                                cursorColor = Color(0xFF0D47A1)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null, tint = Color(0xFF475569))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF0D47A1),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = Color(0xFF0D47A1),
                            unfocusedLabelColor = Color(0xFF64748B),
                            cursorColor = Color(0xFF0D47A1)
                        )
                    )
                    
                    if (!state.isLoginMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password", fontWeight = FontWeight.Medium) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(imageVector = image, contentDescription = null, tint = Color(0xFF475569))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF0D47A1),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFF0D47A1),
                                unfocusedLabelColor = Color(0xFF64748B),
                                cursorColor = Color(0xFF0D47A1)
                            )
                        )
                    }

                    if (true) { // Always show stay signed in option
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = staySignedIn, 
                                onCheckedChange = { 
                                    staySignedIn = it 
                                    viewModel.onEvent(AuthEvent.ToggleStaySignedIn)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0D47A1))
                            )
                            Text("Stay signed in", color = Color(0xFF334155), fontWeight = FontWeight.Medium)
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
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                        ) {
                            Text(if (state.isLoginMode) "Login" else "Register", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    handleGoogleAuth(context, viewModel, state.isLoginMode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("G", fontWeight = FontWeight.Black, color = Color(0xFF0D47A1), fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    if (state.isLoginMode) "Sign in with Google" else "Sign up with Google",
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        TextButton(
                            onClick = { viewModel.onEvent(AuthEvent.ToggleMode) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF0D47A1))
                        ) {
                            Text(
                                if (state.isLoginMode) "Don't have an account? Register" else "Already have an account? Login",
                                fontWeight = FontWeight.Bold
                            )
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
fun GoogleSignUpPasswordDialog(
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Password") },
        text = {
            Column {
                Text("Please set a password for your account so you can also use the login form.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    }
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (password.length >= 6) onConfirm(password) },
                enabled = password.length >= 6 && !isLoading
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
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
                viewModel.onEvent(AuthEvent.SignInWithGoogle(credential.idToken, viewModel.state.value.staySignedIn))
            } else {
                val googleName = credential.displayName ?: "Google User"
                viewModel.onEvent(AuthEvent.PrepareGoogleSignUp(credential.idToken, googleName))
            }
        }
    } catch (e: GetCredentialException) {
        val errorMessage = when (e.type) {
            "androidx.credentials.TYPE_GET_CREDENTIAL_UNSUPPORTED_EXCEPTION" -> "Credentials not supported on this device"
            "com.google.android.gms.common.api.ApiException: 10" -> "Developer Error: Check SHA-1 and Client ID in Firebase Console"
            "com.google.android.gms.common.api.ApiException: 7" -> "Network Error: Please check your connection"
            else -> "Google Auth Error: ${e.message}"
        }
        viewModel.setError(errorMessage)
    } catch (e: Exception) {
        viewModel.setError("An unexpected error occurred: ${e.message}")
    }
}
