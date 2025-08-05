package es.monsteraltech.skincare_tfm.body.mole.performance
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

open class PaginationManager<T>(
    private val pageSize: Int = 20,
    private val prefetchDistance: Int = 5
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isLoading = false
    private var hasMoreData = true
    private var lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null
    private var onLoadStart: (() -> Unit)? = null
    private var onLoadComplete: ((List<T>, Boolean) -> Unit)? = null
    private var onLoadError: ((Exception) -> Unit)? = null
    data class PaginationConfig(
        val pageSize: Int = 20,
        val prefetchDistance: Int = 5,
        val enablePrefetch: Boolean = true,
        val orderBy: String = "createdAt",
        val orderDirection: Query.Direction = Query.Direction.DESCENDING
    )
    fun setCallbacks(
        onLoadStart: (() -> Unit)? = null,
        onLoadComplete: ((List<T>, Boolean) -> Unit)? = null,
        onLoadError: ((Exception) -> Unit)? = null
    ) {
        this.onLoadStart = onLoadStart
        this.onLoadComplete = onLoadComplete
        this.onLoadError = onLoadError
    }
    fun setupScrollListener(
        recyclerView: RecyclerView,
        loadNextPage: suspend () -> Unit
    ) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && hasMoreData &&
                    lastVisibleItem >= totalItemCount - prefetchDistance) {
                    CoroutineScope(Dispatchers.Main).launch {
                        loadNextPage.invoke()
                    }
                }
            }
        })
    }
    suspend fun loadFirstPage(
        collectionPath: String,
        config: PaginationConfig = PaginationConfig(),
        whereClause: ((Query) -> Query)? = null,
        mapper: (com.google.firebase.firestore.DocumentSnapshot) -> T?
    ): Result<List<T>> = withContext(Dispatchers.IO) {
        try {
            reset()
            onLoadStart?.invoke()
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )
            var query: Query = firestore.collection("users")
                .document(currentUser.uid)
                .collection(collectionPath)
                .orderBy(config.orderBy, config.orderDirection)
                .limit(config.pageSize.toLong())
            query = whereClause?.invoke(query) ?: query
            val querySnapshot = query.get().await()
            val items = querySnapshot.documents.mapNotNull { doc ->
                try {
                    mapper(doc)
                } catch (e: Exception) {
                    Log.w("PaginationManager", "Error al mapear documento ${doc.id}", e)
                    null
                }
            }
            lastDocument = querySnapshot.documents.lastOrNull()
            hasMoreData = querySnapshot.documents.size == config.pageSize
            onLoadComplete?.invoke(items, hasMoreData)
            Result.success(items)
        } catch (e: Exception) {
            Log.e("PaginationManager", "Error al cargar primera página", e)
            onLoadError?.invoke(e)
            Result.failure(e)
        }
    }
    suspend fun loadNextPage(
        collectionPath: String,
        config: PaginationConfig = PaginationConfig(),
        whereClause: ((Query) -> Query)? = null,
        mapper: (com.google.firebase.firestore.DocumentSnapshot) -> T?
    ): Result<List<T>> = withContext(Dispatchers.IO) {
        try {
            if (isLoading || !hasMoreData) {
                return@withContext Result.success(emptyList())
            }
            isLoading = true
            onLoadStart?.invoke()
            val currentUser = auth.currentUser ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )
            val lastDoc = lastDocument ?: return@withContext Result.failure(
                Exception("No hay documento de referencia para paginación")
            )
            var query: Query = firestore.collection("users")
                .document(currentUser.uid)
                .collection(collectionPath)
                .orderBy(config.orderBy, config.orderDirection)
                .startAfter(lastDoc)
                .limit(config.pageSize.toLong())
            query = whereClause?.invoke(query) ?: query
            val querySnapshot = query.get().await()
            val items = querySnapshot.documents.mapNotNull { doc ->
                try {
                    mapper(doc)
                } catch (e: Exception) {
                    Log.w("PaginationManager", "Error al mapear documento ${doc.id}", e)
                    null
                }
            }
            lastDocument = querySnapshot.documents.lastOrNull()
            hasMoreData = querySnapshot.documents.size == config.pageSize
            isLoading = false
            onLoadComplete?.invoke(items, hasMoreData)
            Result.success(items)
        } catch (e: Exception) {
            Log.e("PaginationManager", "Error al cargar siguiente página", e)
            isLoading = false
            onLoadError?.invoke(e)
            Result.failure(e)
        }
    }
    fun reset() {
        isLoading = false
        hasMoreData = true
        lastDocument = null
    }
    fun hasMoreData(): Boolean = hasMoreData
    fun isLoading(): Boolean = isLoading
}
class AnalysisPaginationManager(
    pageSize: Int = 20,
    prefetchDistance: Int = 5
) : PaginationManager<AnalysisData>(pageSize, prefetchDistance) {
    suspend fun loadAnalysisPage(
        moleId: String,
        isFirstPage: Boolean = false,
        config: PaginationConfig = PaginationConfig()
    ): Result<List<AnalysisData>> {
        val whereClause: (Query) -> Query = { query ->
            query.whereEqualTo("moleId", moleId)
        }
        val mapper: (com.google.firebase.firestore.DocumentSnapshot) -> AnalysisData? = { doc ->
            try {
                AnalysisData.fromMap(doc.id, doc.data ?: emptyMap())
            } catch (e: Exception) {
                Log.w("AnalysisPaginationManager", "Error al parsear análisis ${doc.id}", e)
                null
            }
        }
        return if (isFirstPage) {
            loadFirstPage("mole_analysis", config, whereClause, mapper)
        } else {
            loadNextPage("mole_analysis", config, whereClause, mapper)
        }
    }
}
class MolePaginationManager(
    pageSize: Int = 15,
    prefetchDistance: Int = 3
) : PaginationManager<es.monsteraltech.skincare_tfm.body.mole.model.MoleData>(pageSize, prefetchDistance) {
    suspend fun loadMolesPage(
        isFirstPage: Boolean = false,
        config: PaginationConfig = PaginationConfig(orderBy = "updatedAt")
    ): Result<List<es.monsteraltech.skincare_tfm.body.mole.model.MoleData>> {
        val mapper: (com.google.firebase.firestore.DocumentSnapshot) -> es.monsteraltech.skincare_tfm.body.mole.model.MoleData? = { doc ->
            try {
                val mole = doc.toObject(es.monsteraltech.skincare_tfm.body.mole.model.MoleData::class.java)
                mole?.copy(id = doc.id)
            } catch (e: Exception) {
                Log.w("MolePaginationManager", "Error al parsear lunar ${doc.id}", e)
                null
            }
        }
        return if (isFirstPage) {
            loadFirstPage("moles", config, null, mapper)
        } else {
            loadNextPage("moles", config, null, mapper)
        }
    }
}