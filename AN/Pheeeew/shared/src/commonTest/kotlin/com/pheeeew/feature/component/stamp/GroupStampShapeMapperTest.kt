package com.pheeeew.feature.component.stamp

import com.pheeeew.domain.model.group.GroupStampFrame
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupStampShapeMapperTest {
    @Test
    fun `모든 backend frame이 UI shape으로 왕복된다`() {
        GroupStampFrame.entries.forEach { frame ->
            assertEquals(frame, frame.toUiShape().toDomainFrame())
        }
    }
}
