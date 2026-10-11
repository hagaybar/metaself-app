package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import javax.inject.Inject

/** D110: every kept version of a weekly plan, each counted from its chain's first keep (D105) — what "planned" is counted by. */
fun interface KeptPlans {
    suspend fun counted(): List<CountedPlan>

    companion object {
        /** No plan was ever kept. */
        val NONE = KeptPlans { emptyList() }
    }
}

/** Over the stored plans; the trainer's request and Your movement's charts (D136) count by the same list. */
class StoredKeptPlans @Inject constructor(private val programmes: ProgrammeStore) : KeptPlans {
    override suspend fun counted(): List<CountedPlan> = keptPlansOf(programmes)
}

/** Every kept version in [programmes], with its chain's counting. */
suspend fun keptPlansOf(programmes: ProgrammeStore): List<CountedPlan> {
    val all = programmes.all()
    return all.filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }.map { programme ->
        val root = Programmes.rootOf(programme, all)
        CountedPlan(programme, PlanCounting(root.id, root.createdAtMillis, programmes.confirmations(root.id)))
    }
}
