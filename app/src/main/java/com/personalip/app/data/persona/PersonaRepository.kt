package com.personalip.app.data.persona

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.personalip.app.data.local.dao.PersonaDao
import com.personalip.app.data.local.entity.PersonaEntity
import com.personalip.app.data.storage.FileNameUtil
import com.personalip.app.data.storage.RootFolderRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 人设仓库：负责 [PersonaEntity] 的读写，并将人设同步落盘到
 * 根目录「人设/persona.json」，方便用户查看与跨设备迁移。
 *
 * 数据库是主存储，JSON 是镜像；保存时同时写两处。
 */
@Singleton
class PersonaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personaDao: PersonaDao,
    private val rootFolderRepository: RootFolderRepository,
    moshi: Moshi
) {
    private val adapter = moshi.adapter(PersonaEntity::class.java)

    fun observe(): Flow<PersonaEntity?> = personaDao.observe()

    suspend fun get(): PersonaEntity? = personaDao.get()

    /**
     * 保存人设：写数据库 + 写 persona.json。
     * @return 是否成功落盘 JSON（数据库一定写入）。
     */
    suspend fun save(persona: PersonaEntity): Boolean = withContext(Dispatchers.IO) {
        val toSave = persona.copy(id = PersonaEntity.SINGLE_ROW_ID)
        personaDao.upsert(toSave)
        saveJson(toSave)
    }

    /** 将人设写入 人设/persona.json。 */
    private suspend fun saveJson(persona: PersonaEntity): Boolean {
        val personaDir = rootFolderRepository.getPersonaDir() ?: return false
        val json = adapter.toJson(persona)
        // 已存在则覆盖。
        val existing = personaDir.findFile(PERSONA_FILE)
        val target = existing ?: personaDir.createFile("application/json", PERSONA_FILE)
            ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
    }

    /** 从 persona.json 重新导入（重装后恢复人设）。 */
    suspend fun importFromJson(): PersonaEntity? = withContext(Dispatchers.IO) {
        val personaDir = rootFolderRepository.getPersonaDir() ?: return@withContext null
        val file = personaDir.findFile(PERSONA_FILE) ?: return@withContext null
        val text = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext null
        val persona = runCatching { adapter.fromJson(text) }.getOrNull() ?: return@withContext null
        personaDao.upsert(persona.copy(id = PersonaEntity.SINGLE_ROW_ID))
        persona
    }

    companion object {
        const val PERSONA_FILE = "persona.json"
    }
}
