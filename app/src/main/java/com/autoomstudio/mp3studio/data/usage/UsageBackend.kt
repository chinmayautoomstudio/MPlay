package com.autoomstudio.mp3studio.data.usage

import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import com.autoomstudio.mp3studio.data.plan.UsageDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** One song the user wants separated; [jobRef] is a new UUID per reservation. */
data class UsageJob(val jobRef: String, val songRef: String)

data class ReserveAnswer(val granted: Set<String>, val denied: Set<String>, val usage: SeparatorUsage)

/** AI Vocal Separator uses on the server (PRD US2, US3). Calls throw when offline or rejected. */
interface UsageBackend {
    suspend fun reserve(jobs: List<UsageJob>): ReserveAnswer

    /** [outcome] is `completed` or `released`. */
    suspend fun finish(jobRef: String, outcome: String): SeparatorUsage
}

/** Calls the `reserve-separation` and `finish-separation` Edge Functions. */
class SupabaseUsageBackend(private val client: SupabaseClient) : UsageBackend {

    override suspend fun reserve(jobs: List<UsageJob>): ReserveAnswer {
        val body = buildJsonObject {
            putJsonArray("jobs") {
                jobs.forEach { job ->
                    addJsonObject {
                        put("jobRef", job.jobRef)
                        put("songRef", job.songRef)
                    }
                }
            }
        }
        val response = client.functions.invoke("reserve-separation", body)
        val dto = json.decodeFromString<ReserveDto>(response.bodyAsText())
        return ReserveAnswer(dto.granted.toSet(), dto.denied.toSet(), dto.usage.toUsage())
    }

    override suspend fun finish(jobRef: String, outcome: String): SeparatorUsage {
        val body = buildJsonObject {
            put("jobRef", jobRef)
            put("outcome", outcome)
        }
        val response = client.functions.invoke("finish-separation", body)
        return json.decodeFromString<FinishDto>(response.bodyAsText()).usage.toUsage()
    }

    @Serializable
    private data class ReserveDto(val granted: List<String>, val denied: List<String>, val usage: UsageDto)

    @Serializable
    private data class FinishDto(val usage: UsageDto)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
