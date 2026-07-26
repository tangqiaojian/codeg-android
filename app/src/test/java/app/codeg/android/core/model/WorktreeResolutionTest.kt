package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `resolve_worktree_folder` decode: snake_case `folder_id` + nullable fields. */
class WorktreeResolutionTest {

    @Test
    fun `decodes path and folder_id`() {
        val r = CodegJson.response.decodeFromString(
            WorktreeResolution.serializer(),
            """{"path":"/repos/feature-wt","folder_id":7}""",
        )
        assertEquals("/repos/feature-wt", r.path)
        assertEquals(7, r.folderId)
    }

    @Test
    fun `decodes explicit nulls`() {
        val r = CodegJson.response.decodeFromString(
            WorktreeResolution.serializer(),
            """{"path":null,"folder_id":null}""",
        )
        assertNull(r.path)
        assertNull(r.folderId)
    }

    @Test
    fun `decodes an empty object to nulls`() {
        val r = CodegJson.response.decodeFromString(WorktreeResolution.serializer(), "{}")
        assertNull(r.path)
        assertNull(r.folderId)
    }
}
