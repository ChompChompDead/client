package me.zeroeightsix.kami.module.modules.combat

import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.manager.managers.CombatManager
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.TpsCalculator
import me.zeroeightsix.kami.util.combat.CombatUtils
import me.zeroeightsix.kami.util.isWeapon
import me.zeroeightsix.kami.util.math.RotationUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.event.listener.listener

@CombatManager.CombatModule
@Module.Info(
    name = "KillAura",
    alias = ["KA", "Aura", "TriggerBot"],
    category = Module.Category.COMBAT,
    description = "Hits entities around you",
    modulePriority = 50
)
object KillAura : Module() {
    private val delayMode = register(Settings.e<WaitMode>("Mode", WaitMode.DELAY))
    private val lockView = register(Settings.booleanBuilder("LockView").withValue(false))
    private val spoofRotation = register(Settings.booleanBuilder("SpoofRotation").withValue(true).withVisibility { !lockView.value })
    private val spamDelay = register(Settings.floatBuilder("SpamDelay").withValue(2.0f).withRange(1.0f, 40.0f).withStep(0.5f).withVisibility { delayMode.value == WaitMode.SPAM })
    val range = register(Settings.floatBuilder("Range").withValue(5f).withRange(0f, 8f).withStep(0.25f))
    private val tpsSync = register(Settings.b("TPSSync", false))
    private val autoWeapon = register(Settings.b("AutoWeapon", true))
    private val weaponOnly = register(Settings.b("WeaponOnly", false))
    private val prefer = register(Settings.enumBuilder(CombatUtils.PreferWeapon::class.java).withName("Prefer").withValue(CombatUtils.PreferWeapon.SWORD).withVisibility { autoWeapon.value })
    private val disableOnDeath = register(Settings.b("DisableOnDeath", false))

    private var inactiveTicks = 0
    private var tickCount = 0

    private enum class WaitMode {
        DELAY, SPAM
    }

    override fun isActive(): Boolean {
        return inactiveTicks <= 20 && isEnabled
    }

    init {
        listener<SafeTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@listener

            inactiveTicks++

            if (mc.player.isDead) {
                if (mc.player.isDead && disableOnDeath.value) disable()
                return@listener
            }

            if (!CombatManager.isOnTopPriority(this) || CombatSetting.pause) return@listener
            val target = CombatManager.target ?: return@listener
            if (mc.player.getDistance(target) > range.value) return@listener

            if (autoWeapon.value) {
                CombatUtils.equipBestWeapon(prefer.value as CombatUtils.PreferWeapon)
            }

            if (weaponOnly.value && !mc.player.heldItemMainhand.item.isWeapon) {
                return@listener
            }

            inactiveTicks = 0
            rotate(target)
            if (canAttack()) attack(target)
        }
    }

    private fun rotate(target: EntityLivingBase) {
        if (lockView.value) {
            RotationUtils.faceEntityClosest(target)
        } else if (spoofRotation.value) {
            val rotation = RotationUtils.getRotationToEntityClosest(target)
            PlayerPacketManager.addPacket(this, PlayerPacketManager.PlayerPacket(rotating = true, rotation = rotation))
        }
    }

    private fun canAttack(): Boolean {
        return if (delayMode.value == WaitMode.DELAY) {
            val adjustTicks = if (!tpsSync.value) 0f else TpsCalculator.adjustTicks
            mc.player.getCooledAttackStrength(adjustTicks) >= 1f
        } else {
            if (tickCount < spamDelay.value) {
                tickCount++
                false
            } else {
                tickCount = 0
                true
            }
        }
    }

    private fun attack(e: Entity) {
        mc.playerController.attackEntity(mc.player, e)
        mc.player.swingArm(EnumHand.MAIN_HAND)
    }
}