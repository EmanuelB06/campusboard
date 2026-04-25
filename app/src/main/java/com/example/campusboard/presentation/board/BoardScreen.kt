package com.example.campusboard.presentation.board

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.PostType
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.JoinRequest
import com.example.campusboard.domain.model.Community
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    val gridColor = Color(0xFFE3F2FD) 
    val baseColor = Color(0xFFF0F9FF) 
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
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
}

private fun showNotification(context: Context, title: String, content: String) {
    val channelId = "campus_board_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Campus Board Notifications", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showCommunityMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val availableCommunities = state.communities.map { it.name }

    if (state.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BoardEvent.CancelLogout) },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(BoardEvent.ConfirmLogout) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BoardEvent.CancelLogout) }) { Text("Cancel") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(Color(0xFF0288D1), Color(0xFF03A9F4))))) {
                    Column(modifier = Modifier.padding(24.dp).align(Alignment.BottomStart)) {
                        Text("CAMPUS BOARD", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                        Text(state.currentUser?.username ?: "Guest", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Surface(modifier = Modifier.padding(top = 4.dp), shape = RoundedCornerShape(4.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Text(text = state.currentUser?.role.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Board Feed", fontWeight = FontWeight.Bold) },
                    selected = state.currentScreen == Screen.BOARD,
                    onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.BOARD)); scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Create Note", fontWeight = FontWeight.Bold) },
                    selected = state.currentScreen == Screen.CREATE,
                    onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.CREATE)); scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.AddCircle, null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Join Community", fontWeight = FontWeight.Bold) },
                    selected = state.currentScreen == Screen.JOIN_COMMUNITY,
                    onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.JOIN_COMMUNITY)); scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.GroupAdd, null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                if (state.currentUser?.role == Role.SUPER_ADMIN || state.currentUser?.role == Role.ADMIN) {
                    NavigationDrawerItem(
                        label = { Text("Join Requests", fontWeight = FontWeight.Bold) },
                        selected = state.currentScreen == Screen.REQUESTS,
                        onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.REQUESTS)); scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.NotificationsActive, null) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                if (state.currentUser?.role == Role.SUPER_ADMIN) {
                    NavigationDrawerItem(
                        label = { Text("Manage Communities", fontWeight = FontWeight.Bold) },
                        selected = state.currentScreen == Screen.MEMBERS,
                        onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.MEMBERS)); scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.Settings, null) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = { viewModel.onEvent(BoardEvent.RequestLogout) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when(state.currentScreen) {
                                Screen.BOARD -> state.selectedCommunity.uppercase()
                                Screen.CREATE -> "NEW NOTE"
                                Screen.MEMBERS -> "MANAGE"
                                Screen.JOIN_COMMUNITY -> "JOIN COMMUNITY"
                                Screen.REQUESTS -> "REQUESTS"
                                else -> "CAMPUS BOARD"
                            },
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0277BD)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, null, tint = Color(0xFF0288D1))
                        }
                    },
                    actions = {
                        if (state.currentScreen == Screen.BOARD) {
                            IconButton(onClick = { showCommunityMenu = true }) {
                                Icon(Icons.Default.FilterList, null, tint = Color(0xFF0288D1))
                            }
                            DropdownMenu(expanded = showCommunityMenu, onDismissRequest = { showCommunityMenu = false }) {
                                availableCommunities.forEach { community ->
                                    if (state.currentUser?.joinedCommunities?.contains(community) == true || community == "General") {
                                        DropdownMenuItem(
                                            text = { Text(community, fontWeight = FontWeight.Bold) },
                                            onClick = { viewModel.onEvent(BoardEvent.SelectCommunity(community)); showCommunityMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                GridBackground()
                Box(modifier = Modifier.padding(padding)) {
                    AnimatedContent(targetState = state.currentScreen, label = "Transition") { screen ->
                        when(screen) {
                            Screen.BOARD -> BoardContent(viewModel)
                            Screen.CREATE -> CreatePostContent(viewModel)
                            Screen.MEMBERS -> MembersAndCommunityContent(viewModel)
                            Screen.JOIN_COMMUNITY -> JoinCommunityContent(viewModel, context)
                            Screen.REQUESTS -> RequestsContent(viewModel, context)
                            else -> Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembersAndCommunityContent(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    var newCommunityName by remember { mutableStateOf("") }
    var newCommunityDesc by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Community") },
            text = {
                Column {
                    OutlinedTextField(value = newCommunityName, onValueChange = { newCommunityName = it }, label = { Text("Name (e.g. BSIT)") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newCommunityDesc, onValueChange = { newCommunityDesc = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.onEvent(BoardEvent.CreateCommunity(newCommunityName, newCommunityDesc))
                    showAddDialog = false
                    newCommunityName = ""
                    newCommunityDesc = ""
                }) { Text("Create") }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("COMMUNITIES", fontWeight = FontWeight.Black, color = Color(0xFF0277BD))
                Button(onClick = { showAddDialog = true }) { Text("Add New") }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(state.communities) { community ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(community.name, fontWeight = FontWeight.Bold)
                    Text(community.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text("USER PERMISSIONS", fontWeight = FontWeight.Black, color = Color(0xFF0277BD))
            Spacer(Modifier.height(8.dp))
        }
        items(state.users) { user ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.username, fontWeight = FontWeight.Bold)
                        Text(user.role.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                    if (user.role != Role.SUPER_ADMIN) {
                        Button(onClick = { viewModel.onEvent(BoardEvent.RequestUpdateRole(user.email, if (user.role == Role.USER) Role.ADMIN else Role.USER)) }) {
                            Text(if (user.role == Role.USER) "Promote" else "Demote")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JoinCommunityContent(viewModel: BoardViewModel, context: Context) {
    val state = viewModel.state.value
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AVAILABLE COMMUNITIES", fontWeight = FontWeight.Black, color = Color(0xFF0277BD))
        Spacer(Modifier.height(16.dp))
        state.communities.forEach { community ->
            if (community.name == "General") return@forEach
            val isJoined = state.currentUser?.joinedCommunities?.contains(community.name) == true
            val pendingRequest = state.joinRequests.find { it.community == community.name && it.status == "PENDING" }
            
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(community.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    
                    when {
                        isJoined -> {
                            Text("JOINED", color = Color(0xFF4CAF50), fontWeight = FontWeight.Black)
                        }
                        pendingRequest != null -> {
                            Surface(color = Color(0xFFFFF9C4), shape = RoundedCornerShape(4.dp)) {
                                Text("PENDING", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFFFBC02D), fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Button(onClick = { 
                                viewModel.onEvent(BoardEvent.SubmitJoinRequest(community.name))
                                showNotification(context, "Request Sent", "Your request to join ${community.name} has been submitted.")
                            }) {
                                Text("REQUEST TO JOIN")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestsContent(viewModel: BoardViewModel, context: Context) {
    val state = viewModel.state.value
    // Filter to only show requests from other users, not the current admin's own requests
    val otherRequests = state.joinRequests.filter { it.userEmail != state.currentUser?.email && it.status == "PENDING" }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (otherRequests.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pending requests", color = Color.Gray)
                }
            }
        }
        items(otherRequests) { request ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("User: ${request.username}", fontWeight = FontWeight.Bold)
                    Text("Community: ${request.community}")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { 
                            viewModel.onEvent(BoardEvent.RejectJoinRequest(request.id))
                            showNotification(context, "Request Rejected", "Joined request for ${request.username} declined.")
                        }) {
                            Text("REJECT", color = Color.Red)
                        }
                        Button(onClick = { 
                            viewModel.onEvent(BoardEvent.AcceptJoinRequest(request.id, request.userEmail, request.community))
                            showNotification(context, "Request Accepted", "${request.username} is now a member of ${request.community}.")
                        }) {
                            Text("ACCEPT")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoardContent(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalItemSpacing = 4.dp
    ) {
        items(state.posts) { post ->
            val canDelete = state.currentUser?.role == Role.SUPER_ADMIN || 
                           (state.currentUser?.role == Role.ADMIN && post.community == state.selectedCommunity)
            PostCard(post = post, onDelete = if (canDelete) { { viewModel.onEvent(BoardEvent.RequestDeletePost(post.id)) } } else null)
        }
    }
}

@Composable
fun PostCard(post: Post, onDelete: (() -> Unit)? = null) {
    val random = remember(post.id) { Random(post.id.hashCode()) }
    val rotation = (random.nextFloat() * 4f) - 2f
    
    Box(
        modifier = Modifier.padding(10.dp).rotate(rotation).shadow(4.dp, RoundedCornerShape(2.dp)).background(Color(post.color)).padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(post.title, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Text(post.type.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Text(post.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("- ${post.author}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostContent(viewModel: BoardViewModel) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PostType.NOTES) }
    var selectedColor by remember { mutableStateOf(Color(0xFFFFF9C4)) }
    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestamp)
    val showDatePicker = remember { mutableStateOf(false) }

    if (showDatePicker.value) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedTimestamp = it }
                    showDatePicker.value = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker.value = false }) { Text("CANCEL") }
            }
        ) { DatePicker(state = datePickerState) }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { 
            viewModel.onEvent(BoardEvent.CreatePost(title, content, selectedType, selectedColor.toArgb(), selectedTimestamp))
            showNotification(context, "Note Pinned", "Your note has been added to the board.")
            viewModel.onEvent(BoardEvent.NavigateTo(Screen.BOARD)) 
        }, modifier = Modifier.fillMaxWidth()) {
            Text("PIN NOTE")
        }
    }
}
