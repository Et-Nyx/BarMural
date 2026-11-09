package me.barmural.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.barmural.data.*
import me.barmural.R
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MuralApp(model: MuralViewModel, login: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var clearSession by remember { mutableStateOf(false) }
    val selected = state.selectedPost
    BackHandler(selected != null) { model.selectPost(null) }
    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("BarMural", fontWeight = FontWeight.Bold); Text("Seu ritmo. Seu espaço.", style = MaterialTheme.typography.labelMedium) } },
            navigationIcon = { if (selected != null) TextButton(onClick = { model.selectPost(null) }) { Text("Voltar") } },
            actions = { if (state.hasSession) TextButton(onClick = { clearSession = true }) { Text("Sair") }
                else TextButton(onClick = login) { Text("Entrar") } }
        ) },
        bottomBar = { if (selected == null) NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) }, label = { Text("Explorar") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1; model.timeline() }, icon = { Icon(painterResource(R.drawable.ic_feed), contentDescription = null) }, label = { Text("Feed") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = null) }, label = { Text("Favoritos") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(painterResource(R.drawable.ic_privacy), contentDescription = null) }, label = { Text("Privacidade") })
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (selected != null) {
                item { PostCard(selected, expanded = true, onProfile = { model.selectPost(null); tab = 0; model.openProfile(it) }, onOpen = {}) }
                item { Button(onClick = model::loadComments, enabled = !state.commentsLoading) { Text("Carregar comentários") } }
                if (state.commentsLoading) item { LoadingIndicator(Modifier.size(48.dp)) }
                state.commentsError?.let { message -> item { MessageCard(message, "Tentar novamente", model::loadComments) } }
                if (state.comments?.isEmpty() == true) item { Text("Nenhum comentário disponível.") }
                items(state.comments.orEmpty()) { comment -> Column { Text("@${comment.author}", fontWeight = FontWeight.Bold); Text(comment.text) } }
            } else when (tab) {
                3 -> {
                    item { Hero("Menos ruído.\nMais controle.", "Sem anúncios próprios, telemetria, sincronização em segundo plano ou acesso à câmera e aos contatos.") }
                    item { InfoCard("O que fica no aparelho", "A sessão fica no armazenamento privado do WebView. Favoritos são apenas nomes de perfis salvos localmente. A sessão não entra no backup do Android.") }
                    item { InfoCard("O que o Instagram recebe", "Consultas, endereço IP, cookies e dados da conta continuam visíveis ao Instagram. Este cliente não oferece anonimato perante a Meta.") }
                    item { InfoCard("Acesso em 2026", "APIs não oficiais podem mudar ou exigir login. Verificações são concluídas no domínio oficial. Nunca solicitamos sua senha em uma tela nativa.") }
                    item { InfoCard("Esta versão", "Leitura de perfis, feed, carrosséis e comentários. Vídeos só iniciam por toque. DMs, Stories, publicação e interações ainda não foram migrados. O feed depende da disponibilidade do endpoint não oficial.") }
                    item { Button(onClick = { clearSession = true }) { Text("Apagar sessão deste aparelho") } }
                }
                2 -> {
                    item { Hero("Perto de quem\nvocê escolhe.", "Seus perfis favoritos, guardados somente neste aparelho.") }
                    if (state.favorites.isEmpty()) item { Text("Busque um perfil e toque em Favoritar para começar.") }
                    items(state.favorites, key = { it }) { name -> FilledTonalButton(onClick = { tab = 0; query = name; model.openProfile(name) }, modifier = Modifier.fillMaxWidth()) { Text("@$name", Modifier.padding(12.dp)) } }
                }
                else -> {
                    if (tab == 0) {
                        item { Hero("Abra uma janela.\nSem pressa.", "Encontre um perfil pelo nome. O Instagram pode exigir uma conta até para conteúdo público.") }
                        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Nome de usuário") }, shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.openProfile(query) }),
                            trailingIcon = { TextButton(onClick = { model.openProfile(query) }, enabled = query.isNotBlank() && !state.loading) { Text("Abrir") } }) }
                    } else item { Hero("Seu feed,\ncom espaço.", "Sem reprodução automática e sem buscar novas páginas até você pedir.") }
                    if (tab == 0 && !state.timeline) state.profile?.let { profile -> item { ProfileCard(profile, profile.username in state.favorites, model::toggleFavorite) } }
                    val showPosts = (tab == 1 && state.timeline) || (tab == 0 && !state.timeline)
                    if (showPosts) {
                        state.error?.let { message -> item { MessageCard(message, if (state.hasSession) "Verificar conta" else "Entrar", login) } }
                        if (state.loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) } }
                        if (!state.loading && state.error == null && state.posts.isEmpty() && (state.profile != null || state.timeline)) item { Text("Nenhuma publicação disponível para este acesso.") }
                        items(state.posts, key = Post::id) { post -> PostCard(post, onProfile = { tab = 0; query = it; model.openProfile(it) }, onOpen = { model.selectPost(post) }) }
                        if (state.cursor != null) item { FilledTonalButton(onClick = model::next, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Carregar mais") } }
                        if (state.profile != null || state.timeline) item { TextButton(onClick = { if (state.timeline) model.timeline() else model.openProfile(state.profile!!.username) }) { Text("Atualizar") } }
                    }
                }
            }
        }
    }
    if (clearSession) AlertDialog(onDismissRequest = { clearSession = false }, title = { Text("Apagar sessão?") },
        text = { Text("Remove os cookies e os dados locais de login. Os favoritos serão mantidos. Isso não encerra outras sessões da sua conta.") },
        confirmButton = { TextButton(onClick = { clearSession = false; model.logout() }) { Text("Apagar sessão") } },
        dismissButton = { TextButton(onClick = { clearSession = false }) { Text("Cancelar") } })
}

@Composable
private fun Hero(title: String, subtitle: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(36.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(body) }
    }
}
@Composable
private fun MessageCard(message: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onAction) { Text(action) } }
    }
}
@Composable
private fun ProfileCard(profile: Profile, favorite: Boolean, toggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(profile.avatar, "Foto de ${profile.username}", Modifier.size(72.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) { Text(profile.name, style = MaterialTheme.typography.titleLarge); Text("@${profile.username}") }
        }
        Text(profile.biography)
        Text("${profile.followers} seguidores${if (profile.privateAccount) " • Conta privada" else ""}")
        FilledTonalButton(onClick = toggle) { Text(if (favorite) "Remover dos favoritos" else "Favoritar") }
    }
}
@Composable
private fun PostCard(post: Post, expanded: Boolean = false, onProfile: (String) -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onProfile(post.author) }) { Text("@${post.author}", fontWeight = FontWeight.Bold) }
            if (post.assets.isNotEmpty()) {
                val pager = rememberPagerState(pageCount = { post.assets.size })
                HorizontalPager(pager) { page ->
                    val asset = post.assets[page]
                    var playing by remember(asset.video) { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        if (playing && asset.video != null && pager.currentPage == page) NativeVideo(asset.video, Modifier.fillMaxSize()) else {
                            AsyncImage(asset.image, "Publicação de ${post.author}, imagem ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            if (asset.video != null) FilledTonalButton(onClick = { playing = true }) { Text("Reproduzir vídeo") }
                        }
                    }
                }
                if (post.assets.size > 1) Text("${pager.currentPage + 1} / ${post.assets.size}", Modifier.padding(horizontal = 16.dp))
            }
            Text(post.caption, Modifier.padding(horizontal = 16.dp), maxLines = if (expanded) Int.MAX_VALUE else 5)
            Text("${post.likes} curtidas • ${post.comments} comentários", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            if (!expanded) TextButton(onClick = onOpen, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Ver publicação") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NativeVideo(url: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val player = remember(url) {
        VideoView(context).apply {
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setVideoPath(url)
            setOnPreparedListener {
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) start()
            }
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.stopPlayback() }
    }
    key(url) { AndroidView(factory = { player }, modifier = modifier) }
}
