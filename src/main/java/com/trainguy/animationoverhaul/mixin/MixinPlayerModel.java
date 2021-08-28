package com.trainguy.animationoverhaul.mixin;

import com.trainguy.animationoverhaul.access.LivingEntityAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.JukeboxBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Unique
@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class MixinPlayerModel<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart jacket;
    @Shadow @Final private ModelPart cloak;

    @Shadow @Final private List<ModelPart> parts;

    public MixinPlayerModel(ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(method = "setupAnim", at = @At("HEAD"), cancellable = true)
    private void animPlayerModel(T livingEntity, float f, float g, float h, float i, float j, CallbackInfo ci){
        // TODO: Rewrite bow and crossbow logic
        // TODO: Remove kneeling when hitting the ground- this is impossible to get working on servers and honestly looks too snappy
        // TODO: Rework the flying and do it in a way that works on servers

        // Make it so this only applies to player animations, not mobs that use player animations as a base.
        if(livingEntity.getType() != EntityType.PLAYER){
            super.setupAnim(livingEntity, f, g, h, i, j);
            return;
        }

        float directionShift = 0;

        float delta = Minecraft.getInstance().getDeltaFrameTime();
        float tickDifference = (float) (h - Math.floor(h));

        // Slow down the limb speed if the entity is a baby
        if(livingEntity.isBaby()){
            f /= 2;
        }

        // Eating weight
        float entityEatingAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("eatingAmount");
        boolean isEating = livingEntity.getUseItem().isEdible() && livingEntity.getTicksUsingItem() != 0;
        float currentEatingAmount = Mth.clamp(entityEatingAmount + (isEating ? -0.125F * delta : 0.125F * delta), 0, 1);
        float eatingAmount = Mth.sin(currentEatingAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;

        // Attack timer
        float entityAttackAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("attackAmount");
        float entityUseAlternateAttack = ((LivingEntityAccess)livingEntity).getAnimationVariable("useAlternateAttack");
        float currentAttackAmount = this.attackTime < 0.1 && this.attackTime > 0 && entityAttackAmount > 0.1F ? 0 : entityAttackAmount;
        entityUseAlternateAttack = currentAttackAmount == 0 && entityAttackAmount < 0.95F ? 1 - entityUseAlternateAttack : entityUseAlternateAttack;

        if(currentAttackAmount == 0){
            if(g < 0.9 && livingEntity.isOnGround()){
                System.out.println("swipe!");
            } else if(livingEntity.fallDistance > 0){
                System.out.println("critical!");
            } else {
                System.out.println("smack!");
            }
            // TODO: determine whether you're in combat or you're just right clicking something with the item
        }

        currentAttackAmount = Math.min(currentAttackAmount + 0.125F * delta, 1);
        ((LivingEntityAccess)livingEntity).setAnimationVariable("attackAmount", currentAttackAmount);
        ((LivingEntityAccess)livingEntity).setAnimationVariable("useAlternateAttack", entityUseAlternateAttack);

        // Dancing weight
        boolean songPlaying = ((LivingEntityAccess)livingEntity).getIsSongPlaying();
        BlockPos songOrigin = ((LivingEntityAccess)livingEntity).getSongOrigin();
        String songName = ((LivingEntityAccess)livingEntity).getSongName();
        float entityDancingAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("dancingAmount");
        float entityDancingFrequency = ((LivingEntityAccess)livingEntity).getAnimationVariable("dancingFrequency");
        float currentDancingAmount = Mth.clamp(entityDancingAmount + (songPlaying && !crouching ? 0.125F * delta : -0.125F * delta), 0, 1);
        float dancingAmount = Mth.sin(currentDancingAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("dancingAmount", currentDancingAmount);

        // Idle weight
        float entityIdleAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("idleAmount");
        boolean isIdle = g <= 0.05 && livingEntity.getDeltaMovement().y < 0.1 && livingEntity.getDeltaMovement().y > -0.1 && !livingEntity.isSleeping() && !livingEntity.isPassenger();
        float currentIdleAmount = Mth.clamp(entityIdleAmount + (isIdle ? 0.125F * delta : -0.125F * delta), 0, 1);
        float idleWeight = Mth.sin(currentIdleAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("idleAmount", currentIdleAmount);

        // Right arm item/block arm pose
        float entityRightArmItemPoseAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("rightArmItemPoseAmount");
        float currentRightArmItemPoseAmount = this.rightArmPose == ArmPose.BLOCK || this.rightArmPose == ArmPose.ITEM ? Mth.clamp(entityRightArmItemPoseAmount + 0.25F * delta, 0.0F, 1.0F) : Mth.clamp(entityRightArmItemPoseAmount - 0.25F * delta, 0.0F, 1.0F);
        float rightArmItemPoseWeight = Mth.sin(currentRightArmItemPoseAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("rightArmItemPoseAmount", currentRightArmItemPoseAmount);

        // Right arm item/block arm pose
        float entityLeftArmItemPoseAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("leftArmItemPoseAmount");
        float currentLeftArmItemPoseAmount = this.leftArmPose == ArmPose.BLOCK || this.leftArmPose == ArmPose.ITEM ? Mth.clamp(entityLeftArmItemPoseAmount + 0.25F * delta, 0.0F, 1.0F) : Mth.clamp(entityLeftArmItemPoseAmount - 0.25F * delta, 0.0F, 1.0F);
        float leftArmItemPoseWeight = Mth.sin(currentLeftArmItemPoseAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("leftArmItemPoseAmount", currentLeftArmItemPoseAmount);

        // Bow pull
        boolean usingBow = this.rightArmPose == ArmPose.BOW_AND_ARROW || this.leftArmPose == ArmPose.BOW_AND_ARROW;
        float entityBowPoseAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("leftArmBowPoseAmount");
        float currentBowPoseAmount = usingBow ? Mth.clamp(entityBowPoseAmount + 0.04F * delta, 0.0F, 1.0F) : Mth.clamp(entityBowPoseAmount - 0.1F * delta, 0.0F, 1.0F);
        float bowHoldingAmount = usingBow ? currentBowPoseAmount < 0.25 ? Mth.sin(currentBowPoseAmount * Mth.PI * 4 - Mth.PI * 0.5F) * 0.5F + 0.5F : 1 : currentBowPoseAmount < 0.5 ? Mth.sin(currentBowPoseAmount * Mth.PI * 2 - Mth.PI * 0.5F) * 0.5F + 0.5F : 1;
        float bowPullAmount = usingBow ? (float) (Mth.cos(Mth.sqrt(currentBowPoseAmount) * Mth.PI * 2) * -0.5 + 0.5) : currentBowPoseAmount < 0.5 ? Mth.sin(currentBowPoseAmount * Mth.PI * 2 - Mth.PI * 0.5F) * 0.5F + 0.5F : 1;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("leftArmBowPoseAmount", currentBowPoseAmount);

        // Crouch variable
        float entityCrouchAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("crouchAmount");
        float currentEntityCrouchAmount = livingEntity.isCrouching() ? Mth.clamp(entityCrouchAmount + 0.125F, 0.0F, 1.0F) : Mth.clamp(entityCrouchAmount - 0.125F, 0.0F, 1.0F);
        float crouchWeight = Mth.sin((currentEntityCrouchAmount) * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("crouchAmount", currentEntityCrouchAmount);

        // Crossbow Holding
        float entityCrossbowPoseAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("leftArmCrossbowPoseAmount");
        boolean holdingOrChargingCrossbow = (livingEntity.getTicksUsingItem() > 0 && livingEntity.getUseItem().getUseAnimation() == UseAnim.CROSSBOW) || this.rightArmPose == ArmPose.CROSSBOW_HOLD || this.leftArmPose == ArmPose.CROSSBOW_HOLD;
        boolean holdingUnloadedCrossbow = (livingEntity.getTicksUsingItem() == 0 && ((livingEntity.getMainHandItem().getUseAnimation() == UseAnim.CROSSBOW && !CrossbowItem.isCharged(livingEntity.getMainHandItem())) || (livingEntity.getOffhandItem().getUseAnimation() == UseAnim.CROSSBOW && !CrossbowItem.isCharged(livingEntity.getOffhandItem()))));
        float currentCrossbowAmount = holdingOrChargingCrossbow ? Mth.clamp(entityCrossbowPoseAmount + 0.25F * delta, 0.0F, 1.0F) : holdingUnloadedCrossbow ? Mth.clamp(entityCrossbowPoseAmount - 0.125F * delta, 0.0F, 1.0F) : Mth.clamp(entityCrossbowPoseAmount - 0.25F * delta, 0.0F, 1.0F);
        float crossbowHoldAmount = !holdingUnloadedCrossbow ? Mth.sin(currentCrossbowAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F : Mth.sin(currentCrossbowAmount * Mth.PI * 1.25F - Mth.PI * 0.5F) * 0.6F + 0.6F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("leftArmCrossbowPoseAmount", currentCrossbowAmount);

        // Simple sprint
        float entitySprintAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("sprintAmount");
        float currentSprintAmount = g > 0.9 ? Mth.clamp(entitySprintAmount + 0.0625F * delta, 0.0F, 1.0F) : Mth.clamp(entitySprintAmount - 0.125F * delta, 0.0F, 1.0F);
        float sprintWeight = Mth.sin(currentSprintAmount * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("sprintAmount", currentSprintAmount);

        // In water
        float entityInWaterAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("inWaterAmount");
        float currentInWaterAmount = Mth.clamp(entityInWaterAmount + (livingEntity.isUnderWater() ? 0.0625F : -0.0625F), 0, 1);
        float inWaterWeight = Mth.sin((currentInWaterAmount) * Mth.PI - Mth.PI * 0.5F) * 0.5F + 0.5F;
        ((LivingEntityAccess)livingEntity).setAnimationVariable("inWaterAmount", currentInWaterAmount);

        /*

        // When the player hits the ground after being in the air, start the hit ground animation
        float entityInAirAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("inAirAmount");
        float entityKneelAmount = ((LivingEntityAccess)livingEntity).getAnimationVariable("kneelAmount");
        float currentKneelAmount;
        if(entityInAirAmount > 0.0F && (livingEntity.isOnGround() || livingEntity.onClimbable())){
            entityKneelAmount = Mth.clamp(entityInAirAmount * 2, 0.0F, 1.0F);
        }
        // Get the in air and kneel values
        currentKneelAmount = Mth.clamp(entityKneelAmount - 0.125F * delta, 0.0F, 1.0F);
        boolean isInAir = (livingEntity.fallDistance > 0 || (livingEntity.getDeltaMovement().y == 0 && !livingEntity.isOnGround())) && !livingEntity.onClimbable() && !livingEntity.isSwimming() && !livingEntity.isPassenger();
        float currentInAirAmount = isInAir ? Mth.clamp(entityInAirAmount + 0.05F * delta, 0.0F, 1.0F) : Mth.clamp(entityInAirAmount - 0.25F * delta, 0.0F, 1.0F);
        float inAirAmount = Mth.clamp(currentInAirAmount, 0, 1);
        float kneelAmount = 2 - Mth.sqrt(Mth.cos((float) (currentKneelAmount * Math.PI * 0.5F))) * 2;
        sprintAmount *= (1 - inAirAmount);
        ((LivingEntityAccess)livingEntity).setAnimationVariable("inAirAmount", currentInAirAmount);
        ((LivingEntityAccess)livingEntity).setAnimationVariable("kneelAmount", currentKneelAmount);

         */

        // Get the look angle and delta movement to determine whether the character is moving forwards or backwards
        // TODO: this breaks when strafing, may need to re-evaluate my approach for this
        if(g > 0.1){
            if((livingEntity.getLookAngle().x > 0 && livingEntity.getDeltaMovement().x < 0) || (livingEntity.getLookAngle().x < 0 && livingEntity.getDeltaMovement().x > 0) || (livingEntity.getLookAngle().z > 0 && livingEntity.getDeltaMovement().z < 0) || (livingEntity.getLookAngle().z < 0 && livingEntity.getDeltaMovement().z > 0)){
                directionShift = 1;
            } else {
                directionShift = 0;
            }
        }

        //tick(livingEntity, delta, g);
        // 0.25 crouching
        // 0.87 walking
        // 1.0 running

        float limbMotionMultiplier = 0.6662F * 0.9F;
        float headRotationMultiplier = 0.017453292F;
        // f = distance total
        // g = is moving lerp
        // h = tick at frame
        // i = y head rot
        // j = x head rot

        // Define part group variables
        List<ModelPart> partListAll = Arrays.asList(this.rightArm, this.leftArm, this.body, this.head, this.rightLeg, this.leftLeg);
        List<ModelPart> partListBody = Arrays.asList(this.rightArm, this.leftArm, this.body, this.head);
        List<ModelPart> partListArms = Arrays.asList(this.rightArm, this.leftArm);
        List<ModelPart> partListLegs = Arrays.asList(this.rightLeg, this.leftLeg);

        // Math for main movements
        float bodyBob = livingEntity.isOnGround() ? (float) ( ((Mth.abs(Mth.sin(f * limbMotionMultiplier - Mth.PI / 4) * 1) * -1 + 1F) * Mth.clamp(g, 0, 0.25) * 4 * (sprintWeight + 1))): 0;
        float rightLegLift = livingEntity.isOnGround() ? (float) ((Math.min(Mth.sin(f * limbMotionMultiplier + Mth.PI * directionShift) * -3, 0) + 1) * Mth.clamp(g, 0, 0.25) * 4) * (1 - inWaterWeight): 0;
        float leftLegLift = livingEntity.isOnGround() ? (float) ((Math.min(Mth.sin(f * limbMotionMultiplier + Mth.PI + Mth.PI * directionShift) * -3, 0) + 1) * Mth.clamp(g, 0, 0.25) * 4) * (1 - inWaterWeight): 0;
        float limbRotation = ((Mth.cos(f * limbMotionMultiplier)) * 1.1F * g) * (1 - inWaterWeight);
        float inverseLimbRotation = ((Mth.cos(f * limbMotionMultiplier + Mth.PI)) * 1.1F * g) * (1 - inWaterWeight);
        float limbForwardMotion = Mth.cos(f * limbMotionMultiplier) * 2.0F * g * (1 - inWaterWeight);
        float inverseLimbForwardMotion = Mth.cos(f * limbMotionMultiplier + Mth.PI) * 2.0F * g * (1 - inWaterWeight);

        // Init the transforms
        initPartTransforms(partListAll);
        this.leftArm.x = 5;
        this.rightArm.x = -5;
        this.leftLeg.x = 2;
        this.rightLeg.x = -2;

        // Main movements
        this.leftArm.z = limbForwardMotion * sprintWeight * 1.5F * (1 - leftArmItemPoseWeight) * (1 - crossbowHoldAmount);
        this.leftArm.y = bodyBob + 2;
        this.leftArm.xRot = limbRotation * (1 - leftArmItemPoseWeight * 0.75F) - (0.25F * leftArmItemPoseWeight * g);
        this.leftArm.yRot = 0;
        this.rightArm.z = inverseLimbForwardMotion * sprintWeight * 1.5F * (1 - rightArmItemPoseWeight) * (1 - crossbowHoldAmount);
        this.rightArm.y = bodyBob + 2;
        this.rightArm.xRot = inverseLimbRotation * (1 - rightArmItemPoseWeight * 0.75F) - (0.25F * rightArmItemPoseWeight * g);
        this.rightArm.yRot = 0;
        this.head.y = bodyBob;
        this.head.xRot = j * headRotationMultiplier;
        this.head.yRot = i * headRotationMultiplier;
        this.body.y = bodyBob;
        this.body.yRot = 0;
        this.rightLeg.z = limbForwardMotion * sprintWeight * 0.75F;
        this.rightLeg.y = rightLegLift + 12;
        this.rightLeg.xRot = limbRotation;
        this.rightLeg.yRot = 0;
        this.rightLeg.zRot = 0;
        this.leftLeg.z = inverseLimbForwardMotion * sprintWeight * 0.75F;
        this.leftLeg.y = leftLegLift + 12;
        this.leftLeg.xRot = inverseLimbRotation;
        this.leftLeg.yRot = 0;
        this.leftLeg.zRot = 0;

        // Neck post process
        this.head.z = j * -0.05F * (1 - crouchWeight);
        this.body.z = j * -0.05F * (1 - crouchWeight);
        this.rightArm.z += j * -0.05F * (1 - crouchWeight);
        this.leftArm.z += j * -0.05F * (1 - crouchWeight);
        this.body.xRot = j * headRotationMultiplier * 0.25F * (1 - crouchWeight);

        // Running post process
        this.head.z += -3.0F * sprintWeight;
        this.body.z += -3.0F * sprintWeight;
        this.rightArm.z += -3.0F * sprintWeight;
        this.leftArm.z += -3.0F * sprintWeight;
        this.body.xRot += 0.25F * sprintWeight;

        // Crouch post process
        if(crouchWeight > 0){
            this.body.xRot += 0.5F * crouchWeight;
            this.rightArm.xRot += 0.4F * crouchWeight;
            this.leftArm.xRot += 0.4F * crouchWeight;
            this.rightLeg.z += 3.9F * crouchWeight + 0.1F;
            this.leftLeg.z += 3.9F * crouchWeight + 0.1F;
            this.rightLeg.y += 0.2F * crouchWeight;
            this.leftLeg.y += 0.2F * crouchWeight;
            this.head.y += 4.2F * crouchWeight;
            this.body.y += 3.2F * crouchWeight;
            this.leftArm.y += 3.2F * crouchWeight;
            this.rightArm.y += 3.2F * crouchWeight;
        }
        if(this.crouching){
            this.rightLeg.y -= 2.0F;
            this.leftLeg.y -= 2.0F;
            this.head.y -= 2.0F;
            this.body.y -= 2.0F;
            this.leftArm.y -= 2.0F;
            this.rightArm.y -= 2.0F;
        }

        // In air post process
        this.leftLeg.z += ((Math.cos(h * limbMotionMultiplier * 0.5) * 2) - 1) * inWaterWeight;
        this.leftLeg.xRot += ((Math.cos(h * limbMotionMultiplier * 0.5 - Mth.PI / 4)) * 0.5) * inWaterWeight;
        this.rightLeg.z += ((Math.cos(h * limbMotionMultiplier * 0.5 - Mth.PI) * 2) - 1) * inWaterWeight;
        this.rightLeg.xRot += ((Math.cos(h * limbMotionMultiplier * 0.5 - Mth.PI / 4 - Mth.PI)) * 0.5) * inWaterWeight;

        this.leftArm.xRot += ((Math.cos(h * limbMotionMultiplier * 0.5 - Mth.PI)) * 0.5) * inWaterWeight;
        this.leftArm.zRot = (float) (((Math.cos(h * limbMotionMultiplier * 0.5 + Mth.PI / 2 - Mth.PI)) * 0.5F - 0.5F) * inWaterWeight);
        this.rightArm.xRot += ((Math.cos(h * limbMotionMultiplier * 0.5)) * 0.5) * inWaterWeight;
        this.rightArm.zRot = (float) (((Math.cos(h * limbMotionMultiplier * 0.5 - Mth.PI / 2)) * 0.5F + 0.5F) * inWaterWeight);

        /*
        Old code that was part of the creative flying animation

        this.leftArm.xRot += 1 * g * inAirAmount;
        this.rightArm.xRot += 1 * g * inAirAmount;
        this.leftLeg.xRot += 1 * g * inAirAmount;
        this.rightLeg.xRot += 1 * g * inAirAmount;
        this.body.xRot += 0.5F * g * inAirAmount;
        this.leftLeg.z += 7 * g * inAirAmount;
        this.rightLeg.z += 7 * g * inAirAmount;
        this.leftLeg.y += -2 * g * inAirAmount;
        this.rightLeg.y += -2 * g * inAirAmount;
         */

        // Kneel post process
        /*
        Old code for the ground impact kneel

        this.leftLeg.y += 2 * kneelAmount;
        this.leftLeg.z += -4 * kneelAmount;
        this.leftLeg.xRot += 0.5 * kneelAmount;
        this.rightLeg.y += 2 * kneelAmount;
        this.rightLeg.z += -4 * kneelAmount;
        this.rightLeg.xRot += 0.5 * kneelAmount;
        this.body.y += 2 * kneelAmount;
        this.body.z += -2 * kneelAmount;
        this.body.xRot += 0.25 * kneelAmount;
        this.leftArm.y += 2 * kneelAmount;
        this.leftArm.z += -2 * kneelAmount;
        this.leftArm.zRot += -0.25F * kneelAmount;
        this.rightArm.y += 2 * kneelAmount;
        this.rightArm.z += -2 * kneelAmount;
        this.rightArm.zRot += 0.25F * kneelAmount;
        this.head.y += 2 * kneelAmount;
        this.head.z += -3 * kneelAmount;
         */


        // Dancing animation post process

        dancingAmount *= idleWeight;
        idleWeight *= 1 - dancingAmount;

        float dancingDirectionCurve = Mth.sin(h / 10 * Mth.PI / 2 + Mth.PI / 4);
        for(ModelPart part : partListBody){
            part.y += (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 1.5F * dancingAmount;
            part.x += dancingDirectionCurve * 1 * dancingAmount;
        }
        this.body.yRot += dancingDirectionCurve * 0.4 * dancingAmount;
        this.body.zRot += dancingDirectionCurve * 0.1 * dancingAmount;
        for(ModelPart part : partListLegs){
            part.x += dancingDirectionCurve * -0.5 * dancingAmount;
            part.zRot += dancingDirectionCurve * -0.0625 * dancingAmount;
        }

        this.rightLeg.xRot += (dancingDirectionCurve > 0 ? -dancingDirectionCurve * 0.5F : (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 0.1) * dancingAmount;
        this.rightLeg.yRot += (dancingDirectionCurve > 0 ? dancingDirectionCurve * 0.5F : dancingDirectionCurve * 0.125F) * dancingAmount;
        this.rightLeg.zRot += (dancingDirectionCurve > 0 ? dancingDirectionCurve * 0.125F : 0) * dancingAmount;
        this.rightLeg.z += (dancingDirectionCurve > 0 ? 0 : (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * -1) * dancingAmount;
        this.rightLeg.y += (dancingDirectionCurve > 0 ? (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 2 + -dancingDirectionCurve * 1 : (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 0.2) * dancingAmount;
        this.leftLeg.xRot += (dancingDirectionCurve < 0 ? dancingDirectionCurve * 0.5F : (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 0.1) * dancingAmount;
        this.leftLeg.yRot += (dancingDirectionCurve < 0 ? dancingDirectionCurve * 0.5F : dancingDirectionCurve * 0.125F) * dancingAmount;
        this.leftLeg.zRot += (dancingDirectionCurve < 0 ? dancingDirectionCurve * 0.125F : 0) * dancingAmount;
        this.leftLeg.z += (dancingDirectionCurve < 0 ? 0 : (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * -1) * dancingAmount;
        this.leftLeg.y += (dancingDirectionCurve < 0 ? (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 2 + dancingDirectionCurve * 1 : (Mth.sin(h * Mth.PI / 5 + Mth.PI / 2) * 0.5F + 0.5F) * 0.2) * dancingAmount;

        this.rightArm.xRot += (-3 + (Mth.sin(h * Mth.PI / 5) * 0.5F + 0.5F) * 0.25) * dancingAmount;
        this.rightArm.zRot += -0.25 * dancingAmount;
        this.rightArm.z += dancingDirectionCurve * 2 * dancingAmount;
        this.leftArm.xRot += (-3 + (Mth.sin(h * Mth.PI / 5) * 0.5F + 0.5F) * 0.25) * dancingAmount;
        this.leftArm.zRot += 0.25 * dancingAmount;
        this.leftArm.z += dancingDirectionCurve * -2 * dancingAmount;

        // Idle animation post process
        float idleBreathingZMovement = (Mth.sin(h * Mth.PI / 24) * -0.25F - (1 / 4.0F));
        this.body.xRot += (Mth.sin(h * Mth.PI / 24) * 0.0625 * (1 / 3F) + (0.0625 / 3)) * idleWeight;
        this.body.z += idleBreathingZMovement * idleWeight;
        this.body.y += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 2)) * 0.125) * idleWeight;
        this.head.z += idleBreathingZMovement * idleWeight;
        this.head.y += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 2)) * 0.125) * idleWeight;

        for(ModelPart part : partListArms){
            part.z += idleBreathingZMovement * idleWeight;
            part.y += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 2)) * 0.125) * idleWeight;
        }
        this.rightArm.zRot += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 1)) * -0.0625 + 0.0625) * idleWeight;
        this.leftArm.zRot += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 1)) * 0.0625 - 0.0625) * idleWeight;
        this.rightArm.yRot += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 4)) * 0.0625) * idleWeight;
        this.leftArm.yRot += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 4)) * -0.0625) * idleWeight;

        this.leftLeg.z += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 2)) * -0.25 - 0.25) * idleWeight;
        this.rightLeg.z += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 4)) * -0.25 - 0.25) * idleWeight;
        this.leftLeg.xRot += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 2)) * 0.02 + 0.02) * idleWeight;
        this.rightLeg.xRot += (Mth.sin(h * Mth.PI / 24 - (Mth.PI / 4)) * 0.02 + 0.02) * idleWeight;

        this.rightLeg.xRot += -0.05 * idleWeight;
        this.rightLeg.yRot += 0.0625 * idleWeight;
        this.rightLeg.z += -0.25 * idleWeight;
        this.leftLeg.xRot += 0.0625 * idleWeight;
        this.leftLeg.zRot += -0.0625 * idleWeight;
        this.leftLeg.yRot += -0.0625 * idleWeight;
        this.leftLeg.z += 0.25 * idleWeight;
        this.leftLeg.y += Mth.sqrt(Mth.sin(idleWeight * Mth.PI)) * -1F;
        for(ModelPart part : partListBody){
            part.y = Mth.lerp(Mth.sqrt(Mth.sin(idleWeight * Mth.PI)), part.y, part.y - 0.5F);
            part.x += Mth.sin(h * Mth.PI / 60) * 0.5F * idleWeight;
            part.z += Mth.sin(h * Mth.PI / 60 + Mth.PI / 2) * 0.5F * idleWeight;
        }
        for(ModelPart part : partListLegs){
            part.x += Mth.sin(h * Mth.PI / 60) * 0.5F * idleWeight;
            part.z += Mth.sin(h * Mth.PI / 60 + Mth.PI / 2) * 0.5F * idleWeight;
            part.zRot += Mth.sin(h * Mth.PI / 60) * 0.03125F * idleWeight;
            part.xRot += Mth.sin(h * Mth.PI / 60 + Mth.PI / 2) * -0.03125F * idleWeight;
        }


        // Right arm pose post process
        this.rightArm.xRot += -0.2F * rightArmItemPoseWeight;

        // Left arm pose post process
        this.leftArm.xRot += -0.2F * leftArmItemPoseWeight;

        // Eating animation post process
        if(livingEntity.getUseItem().isEdible() && livingEntity.getTicksUsingItem() > 0){
            float ticksEating = livingEntity.getUseItemRemainingTicks();
            float ticksEatingLerped = livingEntity.getUseItemRemainingTicks() - tickDifference;
            float eatingWeight = ticksEatingLerped < 27.5 ? ticksEatingLerped < 2.5 ? Mth.sin(ticksEatingLerped * Mth.PI / 5F) : 1 : Mth.sin(ticksEatingLerped * Mth.PI / 5F - Mth.PI);
            this.rightArm.xRot = Mth.lerp(eatingWeight, this.rightArm.xRot, -1.5F);
        }

        // Attack animation post process
        if(currentAttackAmount > 0){
            HumanoidArm humanoidArm = livingEntity.getMainArm();
            humanoidArm = livingEntity.swingingArm == InteractionHand.MAIN_HAND ? humanoidArm : humanoidArm.getOpposite();
            ModelPart attackArmPart = humanoidArm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
            ModelPart attackOffArmPart = humanoidArm == HumanoidArm.RIGHT ? this.leftArm : this.rightArm;

            float entityAttackWeight = currentAttackAmount < 1 - 0.25 ? currentAttackAmount < 0.25 ? Mth.sin(currentAttackAmount * Mth.PI * 2 - Mth.PI * 2) : 1 : Mth.sin(currentAttackAmount * Mth.PI * 2 - Mth.PI * 1);
            float entityAttackEastOut = Mth.sqrt(Mth.sin(currentAttackAmount * Mth.PI - Mth.PI/3F) > 0 ? Mth.sin(currentAttackAmount * Mth.PI - Mth.PI/3F) : 0);
            float entityAttackSwordRaise = Mth.sin(currentAttackAmount * Mth.PI);

            if(livingEntity.getItemInHand(InteractionHand.MAIN_HAND).getItem().toString().contains("sword")){
                attackArmPart.xRot = Mth.lerp(entityAttackWeight * entityAttackSwordRaise, attackArmPart.xRot, -1.5F + this.head.xRot);
                attackArmPart.yRot = Mth.lerp(entityAttackWeight, attackArmPart.yRot, Mth.lerp(entityAttackEastOut, 1F, -1.5F));
                attackArmPart.z += Mth.lerp(entityAttackEastOut, 2F, -3F) * entityAttackWeight;
                attackOffArmPart.z += Mth.lerp(entityAttackEastOut, -1F, 1F) * entityAttackWeight;
                this.body.yRot = Mth.lerp(entityAttackWeight, this.body.yRot, Mth.lerp(entityAttackEastOut, 0.25F, -0.5F));
            } else {
                this.body.yRot += Mth.sin(Mth.sqrt(this.attackTime) * 6.2831855F) * 0.2F;
                // Transform the arm using attack time, which starts at 0 and goes up to 1
                attackArmPart.xRot += (Mth.cos((float) (Math.sqrt(this.attackTime) * Mth.PI * 2)) * -0.5F + 0.5F) * (Mth.clamp(this.head.xRot, -1, 0) - 1);
                attackArmPart.yRot += (Mth.cos((float) (Math.sqrt(this.attackTime) * Mth.PI * 3)) * -0.5F + 0.5F - this.attackTime) * 0.5F;

                if (humanoidArm == HumanoidArm.LEFT) {
                    this.body.yRot *= -1.0F;
                    attackArmPart.yRot *= -1.0F;
                }
            }
        }

        /*
        Old attack animation
        if(this.attackTime > 0){
            // Get the selected arm
            HumanoidArm humanoidArm = livingEntity.getMainArm();
            humanoidArm = livingEntity.swingingArm == InteractionHand.MAIN_HAND ? humanoidArm : humanoidArm.getOpposite();
            ModelPart attackArmPart = humanoidArm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;

            this.body.yRot += Mth.sin(Mth.sqrt(this.attackTime) * 6.2831855F) * 0.2F;
            // Transform the arm using attack time, which starts at 0 and goes up to 1
            attackArmPart.xRot += (Mth.cos((float) (Math.sqrt(this.attackTime) * Mth.PI * 2)) * -0.5F + 0.5F) * (Mth.clamp(this.head.xRot, -1, 0) - 1);
            attackArmPart.yRot += (Mth.cos((float) (Math.sqrt(this.attackTime) * Mth.PI * 3)) * -0.5F + 0.5F - this.attackTime) * 0.5F;

            if (humanoidArm == HumanoidArm.LEFT) {
                this.body.yRot *= -1.0F;
                attackArmPart.yRot *= -1.0F;
            }
        }
        */

        // Bow pull post process
        // Determine which hand should be used as the dominant hand for the bow animation
        boolean isLeftHanded = livingEntity.getMainArm() == HumanoidArm.LEFT;
        boolean holdingBowInRightHand = !isLeftHanded ? livingEntity.getMainHandItem().getUseAnimation() == UseAnim.BOW : livingEntity.getOffhandItem().getUseAnimation() == UseAnim.BOW;
        boolean holdingBowInLeftHand = isLeftHanded ? livingEntity.getMainHandItem().getUseAnimation() == UseAnim.BOW : livingEntity.getOffhandItem().getUseAnimation() == UseAnim.BOW;
        if(holdingBowInRightHand && holdingBowInLeftHand){
            holdingBowInRightHand = !isLeftHanded;
            holdingBowInLeftHand = isLeftHanded;
        }

        if(holdingBowInRightHand){
            this.rightArm.yRot = Mth.lerp(bowHoldingAmount, this.rightArm.yRot , -0.1F + this.head.yRot);
            this.leftArm.yRot = Mth.lerp(bowHoldingAmount, this.leftArm.yRot,0.1F + this.head.yRot + 0.4F);
            this.rightArm.xRot = Mth.lerp(bowHoldingAmount, this.rightArm.xRot, -1.5707964F + this.head.xRot);
            this.leftArm.xRot = Mth.lerp(bowHoldingAmount, this.leftArm.xRot, -1.5707964F + this.head.xRot);
            this.rightArm.zRot = Mth.lerp(bowHoldingAmount, this.rightArm.zRot, 0);

            this.leftArm.zRot = Mth.lerp(bowHoldingAmount, this.leftArm.zRot, this.head.xRot * 0.5F);

            this.leftArm.yRot += Mth.lerp(bowPullAmount, 0, -0.35F);
            if(usingBow){
                this.rightArm.xRot += Mth.lerp(bowPullAmount, 0, 0.3F);
                this.rightArm.yRot += Mth.lerp(bowPullAmount, 0, -0.25F);
            }
        }
        if(holdingBowInLeftHand){
            this.rightArm.yRot = Mth.lerp(bowHoldingAmount, this.rightArm.yRot , -0.1F + this.head.yRot - 0.4F);
            this.leftArm.yRot = Mth.lerp(bowHoldingAmount, this.leftArm.yRot,0.1F + this.head.yRot);
            this.rightArm.xRot = Mth.lerp(bowHoldingAmount, this.rightArm.xRot, -1.5707964F + this.head.xRot);
            this.leftArm.xRot = Mth.lerp(bowHoldingAmount, this.leftArm.xRot, -1.5707964F + this.head.xRot);
            this.leftArm.zRot = Mth.lerp(bowHoldingAmount, this.leftArm.zRot, 0);

            this.rightArm.zRot = Mth.lerp(bowHoldingAmount, this.rightArm.zRot, this.head.xRot * -0.5F);

            this.rightArm.yRot += Mth.lerp(bowPullAmount, 0, 0.35F);
            if(usingBow){
                this.leftArm.xRot += Mth.lerp(bowPullAmount, 0, 0.3F);
                this.leftArm.yRot += Mth.lerp(bowPullAmount, 0, 0.25F);
            }
        }

        // Crossbow hold post process
        // Determine which hand should be used as the dominant hand for the bow animation
        boolean holdingCrossbowInRightHand = !isLeftHanded ? livingEntity.getMainHandItem().getUseAnimation() == UseAnim.CROSSBOW : livingEntity.getOffhandItem().getUseAnimation() == UseAnim.CROSSBOW;
        boolean holdingCrossbowInLeftHand = isLeftHanded ? livingEntity.getMainHandItem().getUseAnimation() == UseAnim.CROSSBOW : livingEntity.getOffhandItem().getUseAnimation() == UseAnim.CROSSBOW;
        if(holdingCrossbowInRightHand && holdingCrossbowInLeftHand){
            holdingCrossbowInRightHand = !isLeftHanded;
            holdingCrossbowInLeftHand = isLeftHanded;
        }
        float crossbowArmBob = livingEntity.isOnGround() ? (Mth.abs(Mth.cos(f * limbMotionMultiplier + Mth.PI / 2)) * 0.125F * g) * -1 : 0;
        ModelPart primaryArm = holdingCrossbowInRightHand ? this.rightArm : this.leftArm;
        ModelPart secondaryArm = holdingCrossbowInRightHand ? this.leftArm : this.rightArm;
        primaryArm.yRot = Mth.lerp(crossbowHoldAmount, primaryArm.yRot, (holdingCrossbowInRightHand ? -0.3F : 0.3F) + this.head.yRot);
        secondaryArm.yRot = Mth.lerp(crossbowHoldAmount, secondaryArm.yRot, (holdingCrossbowInRightHand ? 0.6F : -0.6F) + this.head.yRot);
        primaryArm.xRot = Mth.lerp(crossbowHoldAmount, primaryArm.xRot, -1.5707964F + this.head.xRot + 0.1F + crossbowArmBob);
        secondaryArm.xRot = Mth.lerp(crossbowHoldAmount, secondaryArm.xRot, -1.5F + this.head.xRot + crossbowArmBob);
        primaryArm.zRot = Mth.lerp(crossbowHoldAmount, primaryArm.zRot, 0);
        secondaryArm.zRot = Mth.lerp(crossbowHoldAmount, secondaryArm.zRot, this.head.xRot * (holdingCrossbowInRightHand ? 0.4F : -0.4F));
        // Crossbow pull logic
        if(holdingOrChargingCrossbow){
            if(livingEntity.getTicksUsingItem() > 0){
                float crossbowPullProgress = Mth.clamp(((float)livingEntity.getTicksUsingItem() + tickDifference) / CrossbowItem.getChargeDuration(livingEntity.getUseItem()), 0, 1);
                float crossbowPullCurve = (crossbowPullProgress < 0.75F ? 1 : Mth.sin(crossbowPullProgress * Mth.PI * 4 - Mth.PI / 2) * 0.5F + 0.5F) * crossbowHoldAmount;

                primaryArm.yRot = Mth.lerp(crossbowPullCurve, primaryArm.yRot, holdingCrossbowInRightHand ? -0.8F : 0.8F);
                primaryArm.xRot = Mth.lerp(crossbowPullCurve, primaryArm.xRot, -0.97079635F);

                secondaryArm.yRot = Mth.lerp(crossbowPullCurve, secondaryArm.yRot, Mth.lerp(crossbowPullProgress, 0.4F, 0.85F) * (float)(holdingCrossbowInRightHand ? 1 : -1));
                secondaryArm.xRot = Mth.lerp(crossbowPullCurve, secondaryArm.xRot, Mth.lerp(crossbowPullProgress, primaryArm.xRot, -1.5707964F));
            }
        }

        // debug
        //System.out.println(CrossbowItem.getChargeDuration(livingEntity.getUseItem()));

        // Parent the second layer to the main meshes
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);

        if (livingEntity.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            if (livingEntity.isCrouching()) {
                this.cloak.z = 1.4F;
                this.cloak.y = 1.85F;
            } else {
                this.cloak.z = 0.0F;
                this.cloak.y = 0.0F;
            }
        } else if (livingEntity.isCrouching()) {
            this.cloak.z = 0.3F;
            this.cloak.y = 0.8F;
        } else {
            this.cloak.z = -1.1F;
            this.cloak.y = -0.85F;
        }
        ci.cancel();
    }

    public void initPartTransforms(List<ModelPart> parts){
        for(ModelPart part : parts){
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
        }
    }
}