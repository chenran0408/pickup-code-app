package com.pickupcode.app.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Telephony
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import com.pickupcode.app.ui.miuix.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pickupcode.app.preferences.AppPreferences
import com.pickupcode.app.service.NotificationAppSelection
import com.pickupcode.app.service.SmsNotificationListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAppsScreen(onBack: () -> Unit, onRequestAccess: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val selected by AppPreferences.observeNotificationApps(context).collectAsStateWithLifecycle(initialValue = emptySet())
    val connection by com.pickupcode.app.service.NotificationRecognitionStatus.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(SmsNotificationListener.hasAccess(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = SmsNotificationListener.hasAccess(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var apps by remember { mutableStateOf<List<NotificationAppEntry>>(emptyList()) }
    var defaultSms by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf(emptySet<String>()) }
    val icons = remember { LruCache<String, ImageBitmap>(64) }
    LaunchedEffect(refresh) {
        loading = true
        error = false
        try {
            val loaded = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val launcher = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.packageName }.toSet()
                val entries = pm.getInstalledApplications(0).map { info ->
                    NotificationAppEntry(info.packageName, runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                        system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0, launcher = info.packageName in launcher)
                }
                entries to Telephony.Sms.getDefaultSmsPackage(context)
            }
            apps = loaded.first
            defaultSms = loaded.second
            icons.evictAll()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    val displayed by produceState(emptyList<NotificationAppEntry>(), apps, selected, defaultSms, query, selectedOnly, showSystem) {
        value = withContext(Dispatchers.Default) {
            NotificationAppList.project(apps, selected, defaultSms, context.packageName, query, selectedOnly, showSystem)
        }
    }
    fun choose(app: NotificationAppEntry, checked: Boolean) {
        pending = pending + app.packageName
        val key = NotificationAppSelection.key(app.packageName, defaultSms)
        val write = com.pickupcode.app.App.appScope.async(Dispatchers.IO) {
            AppPreferences.setNotificationAppSelected(context, app.packageName, checked, key)
        }
        scope.launch {
            try {
                write.await()
                if (checked && !SmsNotificationListener.hasAccess(context)) onRequestAccess()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show() }
            finally { pending = pending - app.packageName }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = "通知识别", navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, actions = {
            IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新应用列表") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, onValueChange = { query = it }, labelText = "搜索应用名称或包名",
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清空") } })
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("已选 ${selected.size}") })
                FilterChip(showSystem, onClick = { showSystem = !showSystem }, label = { Text("显示系统应用") })
            }
            Text("勾选应用，自动从新通知中提取取件码。验证码不会保存。", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(com.pickupcode.app.service.NotificationRecognitionStatus.description(selected.size, granted, connection),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = if (connection.connection == com.pickupcode.app.service.NotificationRecognitionStatus.Connection.FAILED)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(horizontal = 8.dp)) {
                Row {
                TextButton(onClick = onRequestAccess) { Text("通知访问权限") }
                if (selected.isNotEmpty() && granted && !connection.connected) TextButton(
                    onClick = { com.pickupcode.app.service.NotificationListenerConnection.recover(context) },
                    enabled = connection.connection != com.pickupcode.app.service.NotificationRecognitionStatus.Connection.CONNECTING,
                ) { Text("重新连接") }
                }
                if (connection.connection == com.pickupcode.app.service.NotificationRecognitionStatus.Connection.FAILED) TextButton(
                    onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                ) { Text("后台设置") }
            }
            if (error) {
                Text("无法读取应用列表，请检查系统的应用列表权限后重试", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refresh++ }) { Text("重试") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            else if (displayed.isEmpty()) Text(if (selectedOnly) "没有符合搜索条件的已选应用" else "没有符合搜索条件的应用", Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(displayed, key = { it.packageName }) { app ->
                    val checked = NotificationAppSelection.contains(selected, app.packageName, defaultSms)
                    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = app.packageName !in pending,
                        role = Role.Checkbox, onValueChange = { choose(app, it) }).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val icon by produceState<ImageBitmap?>(null, app.packageName, refresh) {
                            value = withContext(Dispatchers.IO) {
                                icons.get(app.packageName) ?: runCatching {
                                    context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
                                }.getOrNull()?.also { icons.put(app.packageName, it) }
                            }
                        }
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            icon?.let { Image(it, null, Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when { !app.available -> "未安装或不可见，可取消选择"
                                app.packageName == defaultSms -> "默认短信应用（含网络短信）"
                                else -> app.packageName }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(checked, onCheckedChange = null, enabled = app.packageName !in pending)
                    }
                    HorizontalDivider(Modifier.padding(start = 68.dp))
                }
            }
        }
    }
}
