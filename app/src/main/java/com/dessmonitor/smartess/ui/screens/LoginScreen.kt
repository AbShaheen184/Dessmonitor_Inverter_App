package com.dessmonitor.smartess.ui.screens

import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    repository: DeviceRepository,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var companyKey by remember { mutableStateOf("bnrl_frRFjEz8Mkn") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val usernameNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Username, AutofillType.EmailAddress),
            onFill = { username = it }
        )
    }

    val passwordNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { password = it }
        )
    }

    DisposableEffect(autofillTree) {
        autofillTree.children[usernameNode.id] = usernameNode
        autofillTree.children[passwordNode.id] = passwordNode
        onDispose {
            autofillTree.children.remove(usernameNode.id)
            autofillTree.children.remove(passwordNode.id)
        }
    }

    fun performLogin() {
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "Please enter username and password"
            return
        }
        
        // Clear focus to dismiss soft keyboard and signal input completion to Autofill Framework
        focusManager.clearFocus()
        
        // Explicitly send value updates to system Autofill Service right before submitting
        autofillManager?.notifyValueChanged(view, usernameNode.id, AutofillValue.forText(username))
        autofillManager?.notifyValueChanged(view, passwordNode.id, AutofillValue.forText(password))
        
        isLoading = true
        errorMessage = null
        
        scope.launch {
            val result = repository.login(username, password, companyKey)
            isLoading = false
            result.onSuccess {
                // Inform the Android framework that the form was completed and submitted successfully
                autofillManager?.commit()
                onLoginSuccess()
            }.onFailure {
                errorMessage = it.message ?: "Authentication failed"
            }
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SolarPower,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "SmartESS Monitor",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Welcome back, please sign in",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(40.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        autofillManager?.notifyValueChanged(view, usernameNode.id, AutofillValue.forText(it))
                    },
                    label = { Text("Username / Email") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { 
                            usernameNode.boundingBox = it.boundsInWindow() 
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                autofill?.requestAutofillForNode(usernameNode)
                                autofillManager?.notifyViewEntered(view, usernameNode.id, usernameNode.boundingBox?.let { 
                                    android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) 
                                } ?: android.graphics.Rect())
                            } else {
                                autofillManager?.notifyViewExited(view, usernameNode.id)
                            }
                        },
                    shape = MaterialTheme.shapes.large
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        autofillManager?.notifyValueChanged(view, passwordNode.id, AutofillValue.forText(it))
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { performLogin() }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { 
                            passwordNode.boundingBox = it.boundsInWindow() 
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                autofill?.requestAutofillForNode(passwordNode)
                                autofillManager?.notifyViewEntered(view, passwordNode.id, passwordNode.boundingBox?.let { 
                                    android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) 
                                } ?: android.graphics.Rect())
                            } else {
                                autofillManager?.notifyViewExited(view, passwordNode.id)
                            }
                        },
                    shape = MaterialTheme.shapes.large
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = companyKey,
                    onValueChange = { companyKey = it },
                    label = { Text("Company Key") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { performLogin() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
