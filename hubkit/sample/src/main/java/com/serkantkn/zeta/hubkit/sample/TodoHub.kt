package com.serkantkn.zeta.hubkit.sample

import com.serkantkn.zeta.hubkit.HubIcon
import com.serkantkn.zeta.hubkit.HubSpec
import com.serkantkn.zeta.hubkit.TextStyle
import com.serkantkn.zeta.hubkit.ZetaHubService
import com.serkantkn.zeta.hubkit.subPage
import com.serkantkn.zeta.hubkit.zetaHub
import java.text.DateFormat
import java.util.Date

/**
 * A to-do list as a Zeta hub: two pages, a command to add, a page for each task, a live tile.
 * Kept in memory only - it is an example of ZetaHubKit, not a to-do app.
 */
class TodoHub : ZetaHubService() {

    private data class Task(val id: Int, val title: String, val added: Long, val done: Boolean = false)

    private var nextId = 1
    private val ideas = listOf(
        "süt al", "faturayı öde", "annemi ara", "kitabı iade et", "sunumu bitir",
        "bisikleti tamir et", "çiçekleri sula", "spor salonuna git"
    )
    private var tasks by mutableHubStateOf(
        listOf("süt al", "faturayı öde", "annemi ara").map { Task(nextId++, it, System.currentTimeMillis()) }
    )

    override fun hub(): HubSpec = zetaHub {
        val open = tasks.filter { !it.done }
        val done = tasks.filter { it.done }

        page(getString(R.string.page_open)) {
            if (open.isEmpty()) empty(getString(R.string.nothing_to_do))
            list(open, key = { it.id }) { task ->
                item(
                    title = task.title,
                    subtitle = added(task),
                    key = task.id,
                    opens = { taskPage(task.id) }
                )
            }
        }
        page(getString(R.string.page_done)) {
            if (done.isEmpty()) empty(getString(R.string.nothing_done))
            list(done, key = { it.id }) { task ->
                item(task.title, subtitle = added(task), key = task.id, opens = { taskPage(task.id) })
            }
        }

        bar {
            action(getString(R.string.add), HubIcon.ADD) { add() }
            menu(getString(R.string.clear_done)) { tasks = tasks.filterNot { it.done } }
        }

        liveTile {
            count = open.size
            line = open.firstOrNull()?.title
        }
    }

    private fun taskPage(id: Int) = subPage(title = tasks.firstOrNull { it.id == id }?.title ?: "", key = "task$id") {
        val task = tasks.firstOrNull { it.id == id }
        if (task == null) {
            empty(getString(R.string.nothing_to_do))
            return@subPage
        }
        section(getString(R.string.details))
        text(added(task), style = TextStyle.CAPTION)
        switch(getString(R.string.done), checked = task.done) { checked ->
            tasks = tasks.map { if (it.id == id) it.copy(done = checked) else it }
        }
        button(getString(R.string.delete)) {
            tasks = tasks.filterNot { it.id == id }
            closeSubPage()
        }
    }

    /** Leaving the hub, the tile is written to Zeta, as it would be after a change in the background. */
    override fun onHubHidden() {
        Thread { publishTile() }.start()
    }

    private fun add() {
        val title = ideas[(nextId - 1) % ideas.size]
        tasks = tasks + Task(nextId++, title, System.currentTimeMillis())
        toast(title)
    }

    private fun added(task: Task): String =
        getString(R.string.added, DateFormat.getTimeInstance(DateFormat.SHORT, hubLocale).format(Date(task.added)))
}
