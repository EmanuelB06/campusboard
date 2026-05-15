package com.example.campusboard.presentation.board

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.campusboard.ui.theme.isLight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.campusboard.domain.model.Post
import com.example.campusboard.domain.model.PostType
import com.example.campusboard.domain.model.Role
import com.example.campusboard.domain.model.Community
import com.example.campusboard.CampusBoardApp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    val gridColor = Color(0xFFE2E8F0) // Slate 200
    val baseColor = Color(0xFFF8FAFC) // Slate 50
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = maxOf(1.dp.toPx(), 40.dp.toPx())
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(BoardEvent.DismissError)
        }
    }
    
    BackHandler(enabled = state.currentScreen != Screen.BOARD) {
        viewModel.onEvent(BoardEvent.NavigateTo(Screen.BOARD))
    }
    var showCommunityMenu by remember { mutableStateOf(false) }
    var helpTextToShow by remember { mutableStateOf<String?>(null) }
    val permissionDescriptions = remember {
        mapOf(
            "can_create_community" to "Allows creating new restricted communities. Useful for top-level administrators.",
            "can_manage_roles" to "Grant or revoke admin status for other users. High-level administrative power.",
            "can_manage_permissions" to "Customize specific administrative capabilities for other admins.",
            "can_manage_requests_globally" to "Review and handle join requests for any community on the platform.",
            "can_approve_posts_globally" to "Review and approve pending posts for all communities, ensuring content quality.",
            "can_manage_bypass_approval" to "Designate trustworthy users who can post without needing admin approval first.",
            "can_delete_any_post" to "Removes any post from the board. Use for moderating inappropriate content.",
            "can_delete_community_posts" to "Allows removing posts within the communities managed by this admin.",
            "can_edit_any_community" to "Allows modifying descriptions and details of any community on the platform.",
            "can_send_global_broadcast" to "Create priority posts that appear at the top for all users across the board.",
            "can_manage_community_users" to "Allows this admin to manage users, roles, and permissions within their assigned communities.",
            "is_suspended" to "Suspend the user's account. They will be unable to log in until unsuspended.",
            "bypass_approval" to "Allow this user to post directly to this community without moderation.",
            "managed_communities" to "The communities this admin is responsible for. They can manage posts and members within these.",
            "promote_admin" to "Promoting a user to Admin allows them to manage specific communities and moderate content.",
            "demote_user" to "Demoting an admin to User removes all their administrative privileges and managed communities.",
            "role_super_admin" to "Super Admins have full control over the entire platform, including all communities and users.",
            "role_admin" to "Admins can manage specific communities, approve posts, and moderate members.",
            "role_user" to "Users can join communities, post notes (subject to approval), and view the board."
        )
    }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val availableCommunities = state.communities.map { it.name }

    if (state.communityWarning != null) {
        val user = state.users.find { it.id == state.communityWarning.first }
        val communityName = state.communityWarning.second
        val isAdding = user?.safeManaged()?.contains(communityName) == false
        val isRemovingLast = !isAdding && user?.safeManaged()?.size == 1
        
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BoardEvent.CancelToggleCommunityManagement) },
            icon = { Icon(Icons.Default.Warning, null, tint = if (isAdding) Color(0xFF0D47A1) else Color(0xFFEAB308)) },
            title = { 
                Text(
                    text = when {
                        isAdding -> "Assign Community"
                        isRemovingLast -> "Critical Warning"
                        else -> "Remove Community"
                    },
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    text = when {
                        isAdding -> "Do you want this admin to manage $communityName?"
                        isRemovingLast -> "This is the ONLY community this admin manages. Removing $communityName will leave them with no administrative responsibilities."
                        else -> "Are you sure you want to remove $communityName from this admin's managed communities?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.onEvent(BoardEvent.ToggleCommunityManagement(state.communityWarning.first, state.communityWarning.second))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdding) Color(0xFF0D47A1) else Color(0xFFEAB308)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text(
                        text = if (isAdding) "Confirm" else "Proceed", 
                        fontWeight = FontWeight.Bold, 
                        color = if (isAdding) Color.White else Color.Black
                    ) 
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BoardEvent.CancelToggleCommunityManagement) }) { 
                    Text("Cancel", color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    if (state.postToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BoardEvent.CancelDeletePost) },
            icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
            title = { Text("Delete Post", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(BoardEvent.ConfirmDeletePost) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BoardEvent.CancelDeletePost) }) { 
                    Text("Cancel", color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    if (state.userToManagePermissions != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BoardEvent.ClosePermissionManager) },
            title = { 
                val canManageGlobal = state.currentUser?.role == Role.SUPER_ADMIN || 
                                     state.currentUser?.safePermissions()?.contains("can_manage_permissions") == true
                Column {
                    Text("Manage Permissions", fontWeight = FontWeight.ExtraBold)
                    Text(
                        state.userToManagePermissions.username, 
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF334155)
                    )
                }
            },
            text = {
                val hasCommunityManagePerm = state.currentUser?.safePermissions()?.contains("can_manage_community_users") == true
                val isTargetUser = state.userToManagePermissions.role == Role.USER
                
                val canManageGlobal = state.currentUser?.role == Role.SUPER_ADMIN || 
                                     state.currentUser?.safePermissions()?.contains("can_manage_permissions") == true
                val canManageRoles = state.currentUser?.role == Role.SUPER_ADMIN || 
                                   state.currentUser?.safePermissions()?.contains("can_manage_roles") == true ||
                                   (hasCommunityManagePerm && isTargetUser)
                val canManageBypass = state.currentUser?.role == Role.SUPER_ADMIN ||
                                    state.currentUser?.safePermissions()?.contains("can_manage_bypass_approval") == true ||
                                    (hasCommunityManagePerm && isTargetUser)

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                    // 1. GLOBAL PERMISSIONS - Only for Superadmin managing an Admin
                    if (state.currentUser?.role == Role.SUPER_ADMIN && state.userToManagePermissions.role == Role.ADMIN) {
                        item {
                            Text(
                                "GLOBAL PERMISSIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF475569)
                            )
                            Spacer(Modifier.height(8.dp))

                            val globalPerms = listOf(
                                "can_create_community" to "Can create communities",
                                "can_edit_any_community" to "Can edit community details",
                                "can_manage_roles" to "Can manage user roles",
                                "can_manage_permissions" to "Can manage permissions",
                                "can_manage_requests_globally" to "Can manage all join requests",
                                "can_approve_posts_globally" to "Can approve all posts",
                                "can_manage_bypass_approval" to "Can manage bypass settings",
                                "can_delete_any_post" to "Can delete any post",
                                "can_send_global_broadcast" to "Can send global broadcasts",
                                "can_manage_community_users" to "Can manage community users"
                            )

                            globalPerms.forEach { (perm, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onEvent(BoardEvent.ToggleGlobalPermission(state.userToManagePermissions.id, perm))
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = state.userToManagePermissions.safePermissions().contains(perm),
                                        onCheckedChange = {
                                            viewModel.onEvent(BoardEvent.ToggleGlobalPermission(state.userToManagePermissions.id, perm))
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0D47A1))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { helpTextToShow = permissionDescriptions[perm] }) {
                                        Icon(Icons.AutoMirrored.Filled.HelpOutline, null, modifier = Modifier.size(18.dp), tint = Color(0xFF64748B))
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // 2. ACCOUNT STATUS - Visible to Superadmin for both Admins and Users
                    if (state.currentUser?.role == Role.SUPER_ADMIN) {
                        item {
                            Text(
                                "ACCOUNT STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF475569)
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onEvent(BoardEvent.ToggleUserSuspension(state.userToManagePermissions.id))
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = state.userToManagePermissions.isSuspended,
                                    onCheckedChange = {
                                        viewModel.onEvent(BoardEvent.ToggleUserSuspension(state.userToManagePermissions.id))
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEF4444))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (state.userToManagePermissions.isSuspended) "Account Suspended" else "Account Active",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (state.userToManagePermissions.isSuspended) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                                IconButton(onClick = { helpTextToShow = permissionDescriptions["is_suspended"] }) {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, modifier = Modifier.size(18.dp), tint = Color(0xFF64748B))
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // 3. COMMUNITY SPECIFIC PERMISSIONS (Post without approval)
                    item {
                        Text(
                            "POST WITHOUT APPROVAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF475569)
                        )
                        Spacer(Modifier.height(8.dp))

                        val filteredJoined = state.userToManagePermissions.safeJoined().filter { it != "General" }
                            filteredJoined.forEach { communityName ->
                                val perm = "bypass_approval_$communityName"
                                val hasPerm = state.userToManagePermissions.safePermissions().contains(perm)
                                val canManageThisBypass = canManageGlobal || 
                                                        state.currentUser?.safePermissions()?.contains("can_manage_bypass_approval") == true ||
                                                        (hasCommunityManagePerm && state.currentUser?.safeManaged()?.contains(communityName) == true)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (canManageThisBypass) Modifier.clickable {
                                            viewModel.onEvent(BoardEvent.ToggleGlobalPermission(state.userToManagePermissions.id, perm))
                                        } else Modifier)
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = hasPerm,
                                        enabled = canManageThisBypass,
                                        onCheckedChange = {
                                            viewModel.onEvent(BoardEvent.ToggleGlobalPermission(state.userToManagePermissions.id, perm))
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0D47A1))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Bypass approval in $communityName", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { helpTextToShow = permissionDescriptions["bypass_approval"] }) {
                                        Icon(Icons.AutoMirrored.Filled.HelpOutline, null, modifier = Modifier.size(18.dp), tint = Color(0xFF64748B))
                                    }
                                }
                            }

                        if (filteredJoined.isEmpty()) {
                            Text("User hasn't joined any restricted communities", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }

                        if (canManageRoles && state.userToManagePermissions.role == Role.ADMIN) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "ASSIGNED COMMUNITIES", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { helpTextToShow = permissionDescriptions["managed_communities"] }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            if (state.communities.isEmpty()) {
                                Text("No communities available", color = Color(0xFF475569))
                            }
                        }
                    }
                    
                    if (canManageRoles && state.userToManagePermissions.role == Role.ADMIN) {
                        items(state.communities) { community ->
                            val isManaged = state.userToManagePermissions.safeManaged().contains(community.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.onEvent(BoardEvent.RequestToggleCommunityManagement(state.userToManagePermissions.id, community.name))
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isManaged,
                                    onCheckedChange = { 
                                        viewModel.onEvent(BoardEvent.RequestToggleCommunityManagement(state.userToManagePermissions.id, community.name))
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0D47A1))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(community.name, fontWeight = FontWeight.Bold)
                                    Text(community.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(BoardEvent.ClosePermissionManager) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Done", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    if (helpTextToShow != null) {
        AlertDialog(
            onDismissRequest = { helpTextToShow = null },
            title = { Text("Permission Detail", fontWeight = FontWeight.Bold) },
            text = { Text(helpTextToShow!!) },
            confirmButton = {
                TextButton(onClick = { helpTextToShow = null }) {
                    Text("Got it", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    if (state.userToPromote != null || state.userToDemote != null) {
        val isPromote = state.userToPromote != null
        var selectedCommunity by remember { mutableStateOf<String?>(null) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BoardEvent.CancelUpdateRole) },
            icon = { Icon(if (isPromote) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (isPromote) Color(0xFF0EA5E9) else Color(0xFFEF4444)) },
            title = { Text(if (isPromote) "Promote to Admin" else "Demote to User", fontWeight = FontWeight.Bold) },
            text = { 
                Column {
                    Text(if (isPromote) "This user will be able to manage posts in their assigned communities." else "This user will lose administrative privileges.")
                    
                    if (isPromote) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select a community for this Admin to manage:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(selectedCommunity ?: "Select Community")
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                state.communities.forEach { community ->
                                    DropdownMenuItem(
                                        text = { Text(community.name) },
                                        onClick = {
                                            selectedCommunity = community.name
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(BoardEvent.ConfirmUpdateRole(selectedCommunity)) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPromote) Color(0xFF0EA5E9) else Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isPromote || selectedCommunity != null
                ) { Text("Confirm", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BoardEvent.CancelUpdateRole) }) { 
                    Text("Cancel", color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    if (state.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BoardEvent.CancelLogout) },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFEF4444)) },
            title = { Text("Logout", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to sign out of your account?", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(BoardEvent.ConfirmLogout) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Logout", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BoardEvent.CancelLogout) }) { 
                    Text("Cancel", color = Color.Black, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFF8FAFC),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1E40AF))))
                ) {
                    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp).align(Alignment.BottomStart)) {
                        Icon(Icons.Default.Dashboard, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("CAMPUS BOARD", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(state.currentUser?.username ?: "Guest", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Surface(modifier = Modifier.padding(top = 4.dp), shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Text(text = state.currentUser?.role.toString(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                val navItems = mutableListOf(
                    Triple("Board Feed", Icons.Default.Dashboard, Screen.BOARD),
                    Triple("Create Note", Icons.Default.AddCircle, Screen.CREATE)
                )

                if (state.currentUser?.role == Role.USER) {
                    navItems.add(Triple("Join Community", Icons.Default.GroupAdd, Screen.JOIN_COMMUNITY))
                }

                if (state.currentUser?.role == Role.SUPER_ADMIN || state.currentUser?.role == Role.ADMIN || state.currentUser?.role == Role.USER) {
                    val label = if (state.currentUser?.role == Role.USER) "My Communities" else "Communities"
                    navItems.add(Triple(label, Icons.Default.Groups, Screen.COMMUNITIES))
                }

                if (state.currentUser?.role == Role.SUPER_ADMIN || 
                    state.currentUser?.safePermissions()?.contains("can_manage_requests_globally") == true ||
                    state.currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true) {
                    navItems.add(Triple("Manage Requests", Icons.Default.NotificationsActive, Screen.REQUESTS))
                }

                val canManageAdmins = state.currentUser?.role == Role.SUPER_ADMIN || 
                                   state.currentUser?.safePermissions()?.contains("can_manage_roles") == true ||
                                   state.currentUser?.safePermissions()?.contains("can_manage_permissions") == true
                val canManageUsers = canManageAdmins || state.currentUser?.safePermissions()?.contains("can_manage_community_users") == true
                
                if (canManageUsers) {
                    navItems.add(Triple("Manage Users", Icons.Default.People, Screen.USERS))
                }

                if (canManageAdmins) {
                    navItems.add(Triple("Manage Admins", Icons.Default.AdminPanelSettings, Screen.ADMINS))
                }

                navItems.forEach { (label, icon, screen) ->
                    NavigationDrawerItem(
                        label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                        selected = state.currentScreen == screen,
                        onClick = { viewModel.onEvent(BoardEvent.NavigateTo(screen)); scope.launch { drawerState.close() } },
                        icon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFFE3F2FD),
                            selectedIconColor = Color(0xFF0D47A1),
                            selectedTextColor = Color(0xFF0D47A1),
                            unselectedIconColor = Color(0xFF334155),
                            unselectedTextColor = Color(0xFF334155)
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text("Sign Out", fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = { viewModel.onEvent(BoardEvent.RequestLogout) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                    modifier = Modifier.padding(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = Color(0xFFEF4444),
                        unselectedTextColor = Color(0xFFEF4444)
                    )
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (state.currentScreen == Screen.BOARD) {
                    FloatingActionButton(
                        onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.CREATE)) },
                        containerColor = Color(0xFF0D47A1),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Post", modifier = Modifier.size(28.dp))
                    }
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when(state.currentScreen) {
                                Screen.BOARD -> if (state.selectedCommunity == "General") "CAMPUS BOARD" else state.selectedCommunity.uppercase()
                                Screen.CREATE -> "NEW NOTE"
                                Screen.COMMUNITIES -> "COMMUNITIES"
                                Screen.COMMUNITY_MEMBERS -> state.communityToViewMembers?.name?.uppercase() ?: "MEMBERS"
                                Screen.JOIN_COMMUNITY -> "JOIN COMMUNITY"
                                Screen.REQUESTS -> "MANAGEMENT"
                                Screen.USERS -> "USER MANAGEMENT"
                                Screen.ADMINS -> "ADMIN MANAGEMENT"
                            },
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0D47A1),
                            letterSpacing = 1.sp
                        )
                    },
                    navigationIcon = {
                        if (state.currentScreen != Screen.BOARD) {
                            IconButton(onClick = { viewModel.onEvent(BoardEvent.NavigateTo(Screen.BOARD)) }) {
                                Icon(Icons.Default.Home, null, tint = Color(0xFF0D47A1))
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, null, tint = Color(0xFF0D47A1))
                            }
                        }
                    },
                    actions = {
                        if (state.currentScreen == Screen.BOARD) {
                            IconButton(onClick = { showCommunityMenu = true }) {
                                Icon(Icons.Default.FilterList, null, tint = Color(0xFF0D47A1))
                            }
                            DropdownMenu(
                                expanded = showCommunityMenu, 
                                onDismissRequest = { showCommunityMenu = false },
                                modifier = Modifier.background(Color.White).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            ) {
                                availableCommunities.forEach { community ->
                                    val isVisible = when (state.currentUser?.role) {
                                        Role.SUPER_ADMIN -> true
                                        Role.ADMIN -> community == "General" || state.currentUser.safeManaged().contains(community)
                                        else -> community == "General" || state.currentUser?.safeJoined()?.contains(community) == true
                                    }
                                    if (isVisible) {
                                        DropdownMenuItem(
                                            text = { Text(community, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
                                            onClick = { viewModel.onEvent(BoardEvent.SelectCommunity(community)); showCommunityMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                val currentUser = state.currentUser
                val hasCommunityManagePerm = currentUser?.safePermissions()?.contains("can_manage_community_users") == true
                val canManageAdmins = currentUser?.role == Role.SUPER_ADMIN || 
                                   currentUser?.safePermissions()?.contains("can_manage_roles") == true ||
                                   currentUser?.safePermissions()?.contains("can_manage_permissions") == true
                val canManageUsers = canManageAdmins || hasCommunityManagePerm
                val canManageRequests = currentUser?.role == Role.SUPER_ADMIN || 
                                       currentUser?.safePermissions()?.contains("can_manage_requests_globally") == true ||
                                       currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true
                
                val showBottomBar = canManageUsers || canManageRequests

                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 8.dp
                    ) {
                        val items = mutableListOf(
                            Triple(Screen.BOARD, "Feed", Icons.Default.Dashboard)
                        )
                        
                        if (canManageUsers) {
                            items.add(Triple(Screen.USERS, "Users", Icons.Default.People))
                        }
                        
                        if (canManageAdmins) {
                            items.add(Triple(Screen.ADMINS, "Admins", Icons.Default.AdminPanelSettings))
                        }
                        
                        items.add(Triple(Screen.COMMUNITIES, "Communities", Icons.Default.Groups))
                        
                        if (canManageRequests) {
                            items.add(Triple(Screen.REQUESTS, "Requests", Icons.Default.NotificationsActive))
                        }

                        items.forEach { (screen, label, icon) ->
                            NavigationBarItem(
                                selected = state.currentScreen == screen,
                                onClick = { viewModel.onEvent(BoardEvent.NavigateTo(screen)) },
                                label = { 
                                    Text(
                                        label, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    ) 
                                },
                                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF0D47A1),
                                    selectedTextColor = Color(0xFF0D47A1),
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color(0xFFE3F2FD)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                GridBackground()
                Box(modifier = Modifier.padding(padding)) {
                    AnimatedContent(targetState = state.currentScreen, label = "Transition") { screen ->
                        when(screen) {
                            Screen.BOARD -> BoardContent(viewModel)
                            Screen.CREATE -> CreatePostContent(viewModel)
                            Screen.COMMUNITIES -> CommunitiesContent(viewModel)
                            Screen.COMMUNITY_MEMBERS -> CommunityMembersContent(viewModel)
                            Screen.JOIN_COMMUNITY -> JoinCommunityContent(viewModel, context)
                            Screen.REQUESTS -> RequestsContent(viewModel, context)
                            Screen.USERS -> UsersManagementContent(viewModel, isAdminManagement = false) { helpKey -> helpTextToShow = permissionDescriptions[helpKey] }
                            Screen.ADMINS -> UsersManagementContent(viewModel, isAdminManagement = true) { helpKey -> helpTextToShow = permissionDescriptions[helpKey] }
                        }
                    }
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0D47A1))
                    }
                }
            }
        }
    }
}


@Composable
fun UsersManagementContent(viewModel: BoardViewModel, isAdminManagement: Boolean, onShowHelp: (String) -> Unit) {
    val state = viewModel.state.value
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredUsers = state.users.filter { 
        val matchesSearch = it.username.contains(searchQuery, ignoreCase = true) || 
                          it.email.contains(searchQuery, ignoreCase = true)
        
        val isSelf = it.id == state.currentUser?.id
        val matchesRole = if (isAdminManagement) it.role == Role.ADMIN else it.role == Role.USER
        
        // Super Admin sees everyone
        // Admin sees users who are in communities they manage
        val hasGlobalManagement = state.currentUser?.role == Role.SUPER_ADMIN ||
                                 state.currentUser?.safePermissions()?.contains("can_manage_roles") == true ||
                                 state.currentUser?.safePermissions()?.contains("can_manage_permissions") == true
        
        val isVisible = if (hasGlobalManagement) {
            true
        } else if (state.currentUser?.role == Role.ADMIN || state.currentUser?.safePermissions()?.contains("can_manage_community_users") == true) {
            val managedComms = state.currentUser?.safeManaged() ?: emptyList()
            val isInMyCommunity = it.safeJoined().any { joined -> managedComms.contains(joined) }
            val managesMyCommunity = it.safeManaged().any { managed -> managedComms.contains(managed) }
            
            if (isAdminManagement) {
                // Newly promoted admins who don't manage any community yet should be visible
                // if they are members of a community the current admin manages.
                // Also show admins who manage communities the current admin manages.
                managesMyCommunity || isInMyCommunity
            } else {
                isInMyCommunity
            }
        } else {
            false
        }

        matchesSearch && matchesRole && isVisible && !isSelf
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isAdminManagement) "Search admins..." else "Search users...") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0D47A1),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )


        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredUsers) { user ->
                val currentUser = state.currentUser
                val isSuperAdmin = currentUser?.role == Role.SUPER_ADMIN
                val hasGlobalRoles = isSuperAdmin || currentUser?.safePermissions()?.contains("can_manage_roles") == true
                val hasGlobalPerms = isSuperAdmin || currentUser?.safePermissions()?.contains("can_manage_permissions") == true
                val hasGlobalBypass = isSuperAdmin || currentUser?.safePermissions()?.contains("can_manage_bypass_approval") == true
                val hasCommunityManage = currentUser?.safePermissions()?.contains("can_manage_community_users") == true
                
                val isMyCommunityUser = user.safeJoined().any { currentUser?.safeManaged()?.contains(it) == true }
                
                val canManageRoles = if (user.role == Role.USER) isSuperAdmin else hasGlobalRoles
                val canManagePermissions = hasGlobalPerms || (hasCommunityManage && isMyCommunityUser && user.role == Role.USER)
                val canManageBypass = hasGlobalBypass || (hasCommunityManage && isMyCommunityUser && user.role == Role.USER)
                
                UserCard(
                    user = user, 
                    viewModel = viewModel,
                    canManageRoles = canManageRoles,
                    canManagePermissions = canManagePermissions,
                    canManageBypass = canManageBypass,
                    isAdminManagementView = isAdminManagement,
                    onUpdateRole = { targetRole ->
                        viewModel.onEvent(BoardEvent.RequestUpdateRole(user.id, targetRole))
                    },
                    onShowHelp = onShowHelp
                )
            }
        }
    }
}


@Composable
fun UserCard(
    user: com.example.campusboard.domain.model.User, 
    viewModel: BoardViewModel, 
    canManageRoles: Boolean,
    canManagePermissions: Boolean, 
    canManageBypass: Boolean,
    isAdminManagementView: Boolean,
    onUpdateRole: (Role) -> Unit,
    onShowHelp: (String) -> Unit
) {
    val state = viewModel.state.value
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.username.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.username,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF334155)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (user.role) {
                        Role.SUPER_ADMIN -> Color(0xFFFEE2E2)
                        Role.ADMIN -> Color(0xFFE0F2FE)
                        Role.USER -> Color(0xFFF1F5F9)
                    },
                    modifier = Modifier.clickable { 
                        onShowHelp(when(user.role) {
                            Role.SUPER_ADMIN -> "role_super_admin"
                            Role.ADMIN -> "role_admin"
                            else -> "role_user"
                        })
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            user.role.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = when (user.role) {
                                Role.SUPER_ADMIN -> Color(0xFFEF4444)
                                Role.ADMIN -> Color(0xFF0EA5E9)
                                Role.USER -> Color(0xFF475569)
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline, 
                            null, 
                            modifier = Modifier.size(12.dp),
                            tint = (when (user.role) {
                                Role.SUPER_ADMIN -> Color(0xFFEF4444)
                                Role.ADMIN -> Color(0xFF0EA5E9)
                                Role.USER -> Color(0xFF475569)
                            }).copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (user.role != Role.USER) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "MANAGED COMMUNITIES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF475569),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (user.role == Role.SUPER_ADMIN) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                "ALL COMMUNITIES",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    } else {
                        user.safeManaged().forEach { community ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    community,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        if (user.safeManaged().isEmpty()) {
                            Text(
                                "No communities managed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            if (user.role != Role.SUPER_ADMIN && (canManageRoles || canManagePermissions || canManageBypass)) {
                val showPermissions = (user.role == Role.ADMIN && canManagePermissions) || (user.role == Role.USER && canManageBypass)

                if (showPermissions || canManageRoles) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showPermissions) {
                            TextButton(
                                onClick = { viewModel.onEvent(BoardEvent.OpenPermissionManager(user)) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF0D47A1))
                            ) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Permissions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (canManageRoles) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onUpdateRole(if (user.role == Role.ADMIN) Role.USER else Role.ADMIN) },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (user.role == Role.ADMIN) Color(0xFFEF4444) else Color(0xFF0EA5E9)
                                    )
                                ) {
                                    Icon(
                                        if (user.role == Role.ADMIN) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (user.role == Role.ADMIN) "Demote to User" else "Promote to Admin",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                IconButton(
                                    onClick = { 
                                        onShowHelp(if (user.role == Role.ADMIN) "demote_user" else "promote_admin")
                                    }, 
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun CommunitiesContent(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCommunityName by remember { mutableStateOf("") }
    var newCommunityDesc by remember { mutableStateOf("") }

    val filteredCommunities = if (state.currentUser?.role == Role.ADMIN || state.currentUser?.role == Role.USER) {
        state.communities.filter { 
            it.name == "General" || 
            state.currentUser.safeManaged().contains(it.name) ||
            state.currentUser.safeJoined().contains(it.name)
        }
    } else {
        state.communities
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredCommunities) { community ->
                CommunityCard(
                    community = community,
                    viewModel = viewModel,
                    onViewMembers = { viewModel.onEvent(BoardEvent.ViewCommunityMembers(community)) }
                )
            }
        }

        if (state.communities.isEmpty() && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Groups, null, modifier = Modifier.size(64.dp), tint = Color(0xFFCBD5E1))
                    Spacer(Modifier.height(16.dp))
                    Text("No communities found", style = MaterialTheme.typography.titleMedium, color = Color(0xFF334155))
                }
            }
        }

        if (state.currentUser?.role == Role.SUPER_ADMIN || state.currentUser?.safePermissions()?.contains("can_create_community") == true) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = Color(0xFF0D47A1),
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, null) }
        }

        if (showCreateDialog) {
            CommunityEditorDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, desc -> 
                    viewModel.onEvent(BoardEvent.CreateCommunity(name, desc))
                }
            )
        }

        if (state.communityToEdit != null) {
            CommunityEditorDialog(
                community = state.communityToEdit,
                onDismiss = { viewModel.onEvent(BoardEvent.CloseCommunityEditor) },
                onConfirm = { name, desc -> 
                    viewModel.onEvent(BoardEvent.UpdateCommunity(name, desc))
                }
            )
        }
    }
}


@Composable
fun CommunityEditorDialog(
    community: Community? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(community?.name ?: "") }
    var desc by remember { mutableStateOf(community?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (community == null) "Create Community" else "Edit Community") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name") }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = community == null // Don't allow renaming for now to avoid ID issues
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, 
                    onValueChange = { desc = it }, 
                    label = { Text("Description") }, 
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, desc); onDismiss() }) { 
                Text(if (community == null) "Create" else "Save") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CommunityCard(
    community: Community,
    viewModel: BoardViewModel,
    onViewMembers: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        community.name,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF0D47A1)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        community.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155),
                        lineHeight = 20.sp
                    )
                }
                
                // Edit Button
                val canEdit = viewModel.state.value.currentUser?.role == Role.SUPER_ADMIN || 
                             viewModel.state.value.currentUser?.safePermissions()?.contains("can_edit_any_community") == true
                
                if (canEdit) {
                    IconButton(onClick = { viewModel.onEvent(BoardEvent.OpenCommunityEditor(community)) }) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF64748B))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewMembers,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.People, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Members", fontWeight = FontWeight.Bold)
                }

                val currentUser = viewModel.state.value.currentUser
                if (currentUser != null && community.name != "General" && currentUser.safeJoined().contains(community.name)) {
                    Button(
                        onClick = { viewModel.onEvent(BoardEvent.LeaveCommunity(community.name)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFB91C1C)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leave", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun CommunityMembersContent(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    val community = state.communityToViewMembers ?: return
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Members of ${community.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val members = state.membersToView.ifEmpty { state.users.filter { it.safeJoined().contains(community.name) } }
            items(members) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                            Text(user.username.take(1).uppercase(), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user.username, fontWeight = FontWeight.Bold)
                            Text(user.role.name, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
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
    val availableToJoin = state.communities.filter { it.name != "General" && !state.currentUser?.safeJoined()?.contains(it.name)!! }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Available Communities", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (availableToJoin.isEmpty() && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Explore, null, modifier = Modifier.size(64.dp), tint = Color(0xFFCBD5E1))
                    Spacer(Modifier.height(16.dp))
                    Text("No new communities to join", style = MaterialTheme.typography.titleMedium, color = Color(0xFF334155))
                    Text("You are already part of all communities.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(availableToJoin) { community ->
                val pendingRequest = state.myJoinRequests.find { it.community == community.name && it.status == "PENDING" }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(community.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(community.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                        Spacer(Modifier.height(12.dp))
                        
                        if (pendingRequest != null) {
                            Button(
                                onClick = { viewModel.onEvent(BoardEvent.CancelJoinRequest(pendingRequest.id)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text("Cancel Request")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.onEvent(BoardEvent.SubmitJoinRequest(community.name)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                            ) {
                                Text("Request to Join")
                            }
                            Text(
                                "Requesting will auto-leave your current community.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun RequestsContent(viewModel: BoardViewModel, context: Context) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Management",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        Spacer(Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF0D47A1),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF0D47A1)
                )
            },
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Manage Requests", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Post Approvals", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab == 0) {
            JoinRequestsList(viewModel)
        } else {
            PostApprovalsList(viewModel)
        }
    }
}


@Composable
fun JoinRequestsList(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    val canManageGlobally = state.currentUser?.role == Role.SUPER_ADMIN || 
                           state.currentUser?.safePermissions()?.contains("can_manage_requests_globally") == true
    
    val filteredRequests = if (canManageGlobally) {
        state.joinRequests
    } else if (state.currentUser?.role == Role.ADMIN) {
        state.joinRequests.filter { state.currentUser.safeManaged().contains(it.community) }
    } else {
        emptyList()
    }

    if (filteredRequests.isEmpty() && !state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color(0xFFCBD5E1))
                Spacer(Modifier.height(16.dp))
                Text("No pending requests", style = MaterialTheme.typography.titleMedium, color = Color(0xFF334155))
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(filteredRequests) { request ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color(0xFF475569))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(request.username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(request.userEmail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(8.dp)) {
                            Text(request.community, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF0D47A1))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "This user wants to join the ${request.community} community.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(BoardEvent.RejectJoinRequest(request.id)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                        ) { Text("Reject", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { viewModel.onEvent(BoardEvent.AcceptJoinRequest(request.id, request.userId, request.community)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) { Text("Accept", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}


@Composable
fun PostApprovalsList(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    val canApproveGlobally = state.currentUser?.role == Role.SUPER_ADMIN || 
                            state.currentUser?.safePermissions()?.contains("can_approve_posts_globally") == true
    
    val filteredPosts = if (canApproveGlobally) {
        state.pendingPosts
    } else if (state.currentUser?.role == Role.ADMIN) {
        state.pendingPosts.filter { state.currentUser.safeManaged().contains(it.community) }
    } else {
        emptyList()
    }

    if (filteredPosts.isEmpty() && !state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PostAdd, null, modifier = Modifier.size(64.dp), tint = Color(0xFFCBD5E1))
                Spacer(Modifier.height(16.dp))
                Text("No pending post approvals", style = MaterialTheme.typography.titleMedium, color = Color(0xFF334155))
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(filteredPosts) { post ->
            Box(modifier = Modifier.padding(8.dp)) {
                PostCard(post = post, onClick = {}) 
            }
            Card(
                modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(BoardEvent.RejectPostRequest(post.id)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                        ) { Text("Reject", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { viewModel.onEvent(BoardEvent.AcceptPostRequest(post.id, post.community)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) { Text("Approve", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
fun CreatePostContent(viewModel: BoardViewModel) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PostType.NOTES) }
    
    val state = viewModel.state.value
    
    // Community selection logic
    val selectableCommunities = remember(state.currentUser, state.communities) {
        val user = state.currentUser
        when (user?.role) {
            Role.SUPER_ADMIN -> state.communities.map { it.name }
            Role.ADMIN -> listOf("General") + user.safeManaged()
            Role.USER -> listOf("General") + user.safeJoined()
            else -> listOf("General")
        }.distinct()
    }
    
    var selectedCommunityTarget by remember { mutableStateOf(state.selectedCommunity) }
    
    // Ensure selected target is valid when selectable list changes
    LaunchedEffect(selectableCommunities) {
        if (selectedCommunityTarget !in selectableCommunities) {
            selectedCommunityTarget = if ("General" in selectableCommunities) "General" else selectableCommunities.firstOrNull() ?: "General"
        }
    }

    var titleError by remember { mutableStateOf<String?>(null) }
    var contentError by remember { mutableStateOf<String?>(null) }
    
    val colors = listOf(
        Color(0xFFFFF9C4), // Yellow
        Color(0xFFFFE0B2), // Orange
        Color(0xFFF1F8E9), // Green
        Color(0xFFE1F5FE), // Blue
        Color(0xFFF3E5F5), // Purple
        Color(0xFFFCE4EC)  // Pink
    )
    var selectedColor by remember { mutableStateOf(colors[0]) }
    val showDatePicker = remember { mutableStateOf(false) }
    val showTimePicker = remember { mutableStateOf(false) }
    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isBroadcast by remember { mutableStateOf(false) }
    var showCommunityDropdown by remember { mutableStateOf(false) }
    var isPreviewExpanded by remember { mutableStateOf(true) }
    val context = LocalContext.current

    if (showDatePicker.value) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedTimestamp
        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                selectedTimestamp = calendar.timeInMillis
                showDatePicker.value = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    if (showTimePicker.value) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedTimestamp
        val timePickerDialog = android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                selectedTimestamp = calendar.timeInMillis
                showTimePicker.value = false
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isPreviewExpanded = !isPreviewExpanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PREVIEW", 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black, 
                color = Color(0xFF334155),
                letterSpacing = 2.sp
            )
            Icon(
                if (isPreviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF64748B)
            )
        }
        
        AnimatedVisibility(visible = isPreviewExpanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PostCard(
                    post = Post(
                        title = title.ifBlank { "Title Preview" },
                        content = content.ifBlank { "Your content will appear here..." },
                        type = selectedType,
                        color = selectedColor.value.toLong(),
                        timestamp = selectedTimestamp,
                        author = viewModel.state.value.currentUser?.username ?: "You",
                        community = selectedCommunityTarget
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Community Selector
                    Text("POST TO COMMUNITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedCard(
                        onClick = { showCommunityDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (selectedCommunityTarget == "General") Icons.Default.Public else Icons.Default.Groups,
                                    null,
                                    tint = Color(0xFF0D47A1),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(selectedCommunityTarget, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF64748B))
                        }
                    }

                    DropdownMenu(
                        expanded = showCommunityDropdown,
                        onDismissRequest = { showCommunityDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                    ) {
                        selectableCommunities.forEach { community ->
                            DropdownMenuItem(
                                text = { Text(community, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    selectedCommunityTarget = community
                                    showCommunityDropdown = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (community == "General") Icons.Default.Public else Icons.Default.Groups,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title, 
                    onValueChange = { 
                        title = it
                        if (it.isNotBlank()) titleError = null
                    }, 
                    label = { Text("Title", fontWeight = FontWeight.Bold) }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF0D47A1),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = Color(0xFF0D47A1),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = content, 
                    onValueChange = { 
                        content = it
                        if (it.isNotBlank()) contentError = null
                    }, 
                    label = { Text("Content", fontWeight = FontWeight.Bold) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    minLines = 3,
                    isError = contentError != null,
                    supportingText = contentError?.let { { Text(it) } },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF0D47A1),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = Color(0xFF0D47A1),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text("POST TYPE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    PostType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.padding(end = 8.dp),
                            leadingIcon = if (selectedType == type) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE3F2FD),
                                selectedLabelColor = Color(0xFF0D47A1),
                                selectedLeadingIconColor = Color(0xFF0D47A1)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Text("SELECT COLOR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 1.dp,
                                    color = if (selectedColor == color) Color(0xFF0D47A1) else Color(0xFFE2E8F0),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF0D47A1), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                if (viewModel.state.value.currentUser?.role == Role.SUPER_ADMIN || 
                    viewModel.state.value.currentUser?.safePermissions()?.contains("can_send_global_broadcast") == true) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isBroadcast) Color(0xFFFEF2F2) else Color(0xFFF1F5F9))
                            .clickable { isBroadcast = !isBroadcast }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isBroadcast,
                            onCheckedChange = { isBroadcast = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFDC2626),
                                checkedTrackColor = Color(0xFFFCA5A5)
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Make Global Broadcast", fontWeight = FontWeight.Bold, color = if (isBroadcast) Color(0xFF991B1B) else Color.Unspecified)
                            Text("This post will be pinned to the top for everyone.", style = MaterialTheme.typography.bodySmall, color = if (isBroadcast) Color(0xFFB91C1C) else Color(0xFF64748B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedCard(
                        onClick = { showDatePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("DATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                            Text(sdf.format(Date(selectedTimestamp)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        }
                    }
                    OutlinedCard(
                        onClick = { showTimePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("TIME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                            Text(sdf.format(Date(selectedTimestamp)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                if (title.isBlank()) {
                    titleError = "Title is required"
                }
                if (content.isBlank()) {
                    contentError = "Content is required"
                }
                
                if (title.isNotBlank() && content.isNotBlank()) {
                    viewModel.onEvent(BoardEvent.CreatePost(
                        title = title, 
                        content = content, 
                        type = selectedType, 
                        color = selectedColor.value.toLong(), 
                        timestamp = selectedTimestamp,
                        community = selectedCommunityTarget,
                        isBroadcast = isBroadcast
                    ))
                }
            }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
        ) {
            Text("PIN TO BOARD", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardContent(viewModel: BoardViewModel) {
    val state = viewModel.state.value
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<PostType?>(null) }
    var selectedPostForDialog by remember { mutableStateOf<Post?>(null) }
    
    val filteredPosts = state.posts.filter {
        val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true) ||
                it.author.contains(searchQuery, ignoreCase = true) ||
                it.type.name.contains(searchQuery, ignoreCase = true)
        
        val matchesType = selectedType == null || it.type == selectedType
        
        matchesSearch && matchesType
    }

    if (selectedPostForDialog != null) {
        val post = selectedPostForDialog!!
        val canDelete = state.currentUser?.role == Role.SUPER_ADMIN || 
                       state.currentUser?.safePermissions()?.contains("can_delete_any_post") == true ||
                       (state.currentUser?.role == Role.ADMIN && 
                        state.currentUser.safeManaged().contains(post.community) && 
                        state.currentUser.safePermissions().contains("can_delete_community_posts"))
        
        BasicAlertDialog(
            onDismissRequest = { selectedPostForDialog = null },
            modifier = Modifier.padding(16.dp)
        ) {
            PostCard(
                post = post, 
                onDelete = if (canDelete) { { 
                    viewModel.onEvent(BoardEvent.RequestDeletePost(post.id))
                    selectedPostForDialog = null
                } } else null,
                isDialog = true
            )
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search on board...", color = Color(0xFF475569)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF475569)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFF475569))
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF0D47A1),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text("All") },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0D47A1),
                        selectedLabelColor = Color.White,
                        labelColor = Color(0xFF475569)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedType == null,
                        borderColor = Color(0xFFE2E8F0),
                        selectedBorderColor = Color(0xFF0D47A1)
                    )
                )
            }
            items(PostType.entries.toTypedArray()) { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = if (selectedType == type) null else type },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0D47A1),
                        selectedLabelColor = Color.White,
                        labelColor = Color(0xFF475569)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedType == type,
                        borderColor = Color(0xFFE2E8F0),
                        selectedBorderColor = Color(0xFF0D47A1)
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.selectedCommunity == "General") Icons.Default.Public else Icons.Default.Groups,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Viewing ${state.selectedCommunity} Board",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
        }

        if (filteredPosts.isEmpty() && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFFCBD5E1)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isEmpty()) "The board is empty" else "No results for \"$searchQuery\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF475569)
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.onEvent(BoardEvent.Refresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                items(filteredPosts) { post ->
                    val canDelete = state.currentUser?.role == Role.SUPER_ADMIN || 
                                   state.currentUser?.safePermissions()?.contains("can_delete_any_post") == true ||
                                   (state.currentUser?.role == Role.ADMIN && 
                                    state.currentUser.safeManaged().contains(post.community) && 
                                    state.currentUser.safePermissions().contains("can_delete_community_posts"))
                    PostCard(
                        post = post, 
                        onDelete = if (canDelete) { { viewModel.onEvent(BoardEvent.RequestDeletePost(post.id)) } } else null,
                        onClick = { selectedPostForDialog = post }
                    )
                }
            }
        }
    }
}


@Composable
fun PostCard(
    post: Post, 
    onDelete: (() -> Unit)? = null, 
    onClick: (() -> Unit)? = null,
    isDialog: Boolean = false
) {
    val seed = post.id.hashCode().toLong()
    val rotation = remember(seed, isDialog) { if (!isDialog) (Random(seed).nextFloat() * 12f) - 6f else 0f }
    val tiltX = remember(seed, isDialog) { if (!isDialog) (Random(seed + 1).nextFloat() * 4f) - 2f else 0f }
    val tiltY = remember(seed, isDialog) { if (!isDialog) (Random(seed + 2).nextFloat() * 4f) - 2f else 0f }
    
    val pinRotation = remember(seed) { (Random(seed + 3).nextFloat() * 40f) - 20f }
    val noteStyle = remember(seed) { Random(seed + 4).nextInt(4) }
    val heightOffset = remember(seed) { (Random(seed + 5).nextFloat() * 120f - 40f).dp }
    val widthVariation = remember(seed) { (Random(seed + 6).nextFloat() * 16f - 8f).dp }
    val contentPaddingVariation = remember(seed) { (Random(seed + 7).nextFloat() * 12f).dp }
    val titleRotation = remember(seed, isDialog) { if (isDialog) 0f else (Random(seed + 8).nextFloat() * 4f - 2f) }
    val curlAmount = remember(seed) { (Random(seed + 9).nextFloat() * 10f + 5f) }
    var isExpanded by remember { mutableStateOf(isDialog) }
    
    val density = LocalDensity.current

    // Slightly vary the base color for more natural feel
    val baseColor = remember(post.color, post.id) {
        val c = Color(post.color.toULong())
        val r = Random(seed + 10)
        val factor = 0.92f + (r.nextFloat() * 0.16f) // +/- 8%
        Color(
            red = (c.red * factor).coerceIn(0f, 1f),
            green = (c.green * factor).coerceIn(0f, 1f),
            blue = (c.blue * factor).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    
    val noteShape = remember(post.id, curlAmount) {
        val seed = post.id.hashCode().toLong()
        val r = Random(seed)
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            
            moveTo(0f, 0f)
            
            // Top edge: slight natural wave
            cubicTo(w * 0.3f, r.nextFloat() * 4 - 2, w * 0.7f, r.nextFloat() * 4 - 2, w, 0f)
            
            // Right edge: slight irregular curve
            val rightControl = w + (r.nextFloat() * 6 - 2)
            cubicTo(rightControl, h * 0.3f, rightControl, h * 0.7f, w, h)
            
            // Bottom edge: Randomized lifting curl
            val liftLeft = if (r.nextFloat() > 0.5f) curlAmount * 1.5f else 2f
            val liftRight = if (liftLeft < 5f) curlAmount * 1.8f else r.nextFloat() * curlAmount
            
            cubicTo(w * 0.7f, h - liftRight, w * 0.3f, h - liftLeft, 0f, h)
            
            // Left edge: slight natural wave
            val leftControl = r.nextFloat() * 6 - 3
            cubicTo(leftControl, h * 0.7f, leftControl, h * 0.3f, 0f, 0f)
            
            close()
        }
    }

    val shape = noteShape

    val contentColor = if (baseColor.isLight()) Color.Black else Color.White
    val secondaryContentColor = contentColor
    
    val cardModifier = Modifier
        .then(if (isDialog) Modifier.fillMaxWidth() else Modifier.width(intrinsicSize = IntrinsicSize.Max))
        .then(
            if (!isDialog) {
                Modifier
                    .widthIn(min = 160.dp + widthVariation)
                    .then(
                        if (isExpanded) Modifier.heightIn(min = 180.dp + heightOffset)
                        else Modifier.height(180.dp + heightOffset)
                    )
            } else Modifier
        )
        .padding(if (isDialog) 0.dp else 10.dp)
        .graphicsLayer {
            rotationZ = rotation
            cameraDistance = 12f * density.density
        }
        .then(
            Modifier.shadow(
                elevation = if (isDialog) 6.dp else 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
        )
        .then(
            Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        baseColor,
                        Color(
                            red = (baseColor.red * 0.93f).coerceIn(0f, 1f),
                            green = (baseColor.green * 0.93f).coerceIn(0f, 1f),
                            blue = (baseColor.blue * 0.86f).coerceIn(0f, 1f),
                            alpha = 1f
                        )
                    )
                ),
                shape
            )
        )
        .border(0.5.dp, Color.Black.copy(alpha = 0.12f), shape)
        .clickable { 
            if (onClick != null) onClick() else isExpanded = !isExpanded
        }
        .drawWithCache {
            val strokeWidth = 1.dp.toPx()
            val lineSpacing = maxOf(1.dp.toPx(), (if (isDialog) 36.dp else 30.dp).toPx())
            val lineColor = Color.Black.copy(alpha = 0.14f)

            // Paper Texture (subtle noise-like lines)
            val randomDraw = Random(seed + 11)
            val textureLines = List(60) {
                val startX = (randomDraw.nextFloat() * size.width)
                val startY = (randomDraw.nextFloat() * size.height)
                val endX = startX + (randomDraw.nextFloat() * 12f)
                val endY = startY + (randomDraw.nextFloat() * 3f)
                Pair(Offset(startX, startY), Offset(endX, endY))
            }

            // Binder holes
            val holeRadius = (if (isDialog) 5.5.dp else 4.5.dp).toPx()
            val holeSpacing = maxOf(1.dp.toPx(), (if (isDialog) 34.dp else 26.dp).toPx())
            val holeStartY = (if (isDialog) 26.dp else 20.dp).toPx()

            // Folded corner (more realistic)
            val foldSize = (if (isDialog) 28.dp else 22.dp).toPx()
            val foldShadowPath = Path().apply {
                moveTo(size.width, size.height - foldSize)
                lineTo(size.width - foldSize, size.height)
                lineTo(size.width, size.height)
                close()
            }

            val foldFlapPath = Path().apply {
                moveTo(size.width, size.height - foldSize)
                lineTo(size.width - foldSize, size.height)
                lineTo(size.width - foldSize * 0.9f, size.height - foldSize * 0.9f)
                close()
            }

            onDrawBehind {
                // Paper Texture
                textureLines.forEach { (start, end) ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = start,
                        end = end,
                        strokeWidth = 1.2f
                    )
                }

                // Grid or lines
                if (noteStyle == 1 || noteStyle == 3) {
                    var x = lineSpacing
                    while (x < size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                        x += lineSpacing
                    }
                }
                
                var y = lineSpacing * 1.6f
                while (y < size.height - 10.dp.toPx()) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
                    y += lineSpacing
                }

                if (noteStyle == 0 || noteStyle == 2) {
                    val marginX = (if (isDialog) 44.dp else 34.dp).toPx()
                    drawLine(Color(0xFFEF4444).copy(alpha = 0.35f), Offset(marginX, 0f), Offset(marginX, size.height), 1.8.dp.toPx())
                }
                
                // Binder holes
                if (noteStyle == 2) {
                    var holeY = holeStartY
                    while (holeY < size.height - 15.dp.toPx()) {
                        drawCircle(Color.White.copy(0.95f), holeRadius, Offset(12.dp.toPx(), holeY))
                        drawCircle(Color.Black.copy(0.25f), holeRadius, Offset(12.dp.toPx(), holeY), style = Stroke(1.2.dp.toPx()))
                        holeY += holeSpacing
                    }
                }

                // Folded corner
                drawPath(foldShadowPath, Color.Black.copy(alpha = 0.2f))
                drawPath(foldFlapPath, Color.White.copy(alpha = 0.6f))
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(size.width, size.height - foldSize), Offset(size.width - foldSize, size.height), 1.2.dp.toPx())
            }
        }
        .padding(if (isDialog) 24.dp else (16.dp + contentPaddingVariation / 2))

    Box(modifier = cardModifier) {
        // Accessory (Pin, Tape, or Bookmark)
        when (noteStyle) {
            0 -> { // Pin
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                        .size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        null,
                        modifier = Modifier
                            .offset(x = 2.dp, y = 2.dp)
                            .size(22.dp)
                            .rotate(pinRotation),
                        tint = Color.Black.copy(alpha = 0.3f)
                    )
                    Icon(
                        Icons.Default.PushPin,
                        null,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(pinRotation),
                        tint = Color(0xFFDC2626)
                    )
                }
            }
            1 -> { // Tape
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-12).dp, y = (-6).dp)
                        .rotate(-25f)
                        .size(width = 50.dp, height = 20.dp)
                        .background(Color(0xFFFEF08A).copy(alpha = 0.7f))
                        .border(0.5.dp, Color.Black.copy(alpha = 0.1f))
                ) {
                    // Tape texture/edges
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, Color.White.copy(alpha = 0.2f))
                    }
                }
            }
            2 -> { // Spiral Rings
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-10.dp))
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .size(width = 10.dp, height = 18.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFF94A3B8))
                                    ), 
                                    RoundedCornerShape(4.dp)
                                )
                                .border(0.5.dp, Color.Black.copy(0.2f), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            3 -> { // Bookmark/Clip
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-16).dp, y = (-4).dp)
                        .size(width = 18.dp, height = 32.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF472B6), Color(0xFFDB2777))
                            ),
                            RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                        )
                        .border(0.5.dp, Color.Black.copy(0.1f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                )
            }
        }

        val leftMarginPadding = when (noteStyle) {
            0, 2 -> if (isDialog) 36.dp else 28.dp
            1 -> if (isDialog) 44.dp else 36.dp
            else -> 0.dp
        }
        val rightMarginPadding = when (noteStyle) {
            3 -> if (isDialog) 28.dp else 20.dp
            else -> 0.dp
        }

        Column(modifier = Modifier.padding(start = leftMarginPadding, end = rightMarginPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    post.title, 
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f).padding(
                        top = (if (isDialog) 14.dp else 12.dp)
                    ).rotate(titleRotation),
                    style = if (isDialog) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    color = contentColor.copy(alpha = 0.95f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete, 
                        modifier = Modifier
                            .size(if (isDialog) 28.dp else 22.dp)
                            .padding(top = (if (isDialog) 14.dp else 12.dp))
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(if (isDialog) 18.dp else 14.dp), tint = contentColor.copy(alpha = 0.6f))
                    }
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.padding(vertical = (if (isDialog) 6.dp else 4.dp))
            ) {
                val icon = when (post.type) {
                    PostType.NEWS -> Icons.Default.Newspaper
                    PostType.ALERT -> Icons.Default.Warning
                    PostType.EVENT -> Icons.Default.Event
                    PostType.OPPORTUNITY -> Icons.Default.Work
                    PostType.NOTES -> Icons.AutoMirrored.Filled.StickyNote2
                    PostType.OTHERS -> Icons.Default.MoreHoriz
                }
                Icon(icon, null, modifier = Modifier.size(if (isDialog) 16.dp else 12.dp), tint = contentColor.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    post.type.name.lowercase().replaceFirstChar { it.uppercase() }, 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isDialog) 12.sp else 10.sp), 
                    fontWeight = FontWeight.Black, 
                    color = secondaryContentColor,
                    letterSpacing = 0.5.sp
                )
                
                if (post.isBroadcast) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = Color(0xFFDC2626),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "GLOBAL",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isDialog) 10.sp else 8.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(if (isDialog) 12.dp else 8.dp))
            Text(
                post.content, 
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = if (isDialog) 19.sp else 17.sp,
                    lineHeight = if (isDialog) 30.sp else 26.sp,
                    letterSpacing = 0.sp
                ),
                color = contentColor,
                maxLines = if (isExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    bottom = (if (isDialog) 16.dp else 8.dp)
                )
            )
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(if (isDialog) 20.dp else 12.dp))
                HorizontalDivider(color = contentColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(if (isDialog) 14.dp else 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(if (isDialog) 16.dp else 12.dp), tint = secondaryContentColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(post.author, style = if (isDialog) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                    Text(sdf.format(Date(post.timestamp)), style = if (isDialog) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall, color = secondaryContentColor)
                }
            }
        }
    }
}

