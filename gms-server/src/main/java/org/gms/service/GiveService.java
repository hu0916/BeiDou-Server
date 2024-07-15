package org.gms.service;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.inventory.Pet;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.inventory.ItemConstants;
import org.gms.dao.entity.ExtendValueDO;
import org.gms.dto.GiveResourceReqDTO;
import org.gms.exception.BizException;
import org.gms.net.server.Server;
import org.gms.server.CashShop;
import org.gms.server.ItemInformationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static java.util.concurrent.TimeUnit.DAYS;

@Service
@Slf4j
public class GiveService {
    @Autowired
    CharacterService characterService;

    public void give(GiveResourceReqDTO submitData) {
        if (submitData.getPlayerId() == 0) {
            giveAllOnlineChr(submitData);
        } else {
            giveChr(submitData);
        }
    }

    private void giveAllOnlineChr(GiveResourceReqDTO submitData) {
        switch (submitData.getType()) {
            case 0: // nxCredit 点券
            case 1: // nxPrepaid 信用点券
            case 2: // maplePoint 抵用券
                int cashType = switch (submitData.getType()) {
                    case 1 -> CashShop.NX_PREPAID;
                    case 2 -> CashShop.MAPLE_POINT;
                    default -> CashShop.NX_CREDIT;
                };
                giveNxAllOnlineChr(submitData.getQuantity(), cashType);
                break;
            case 3: // mesos
                giveMesosAllOnlineChr(submitData.getQuantity());
                break;
            case 4: // exp
                giveExpAllOnlineChr(submitData.getQuantity());
                break;
            case 5: // item
                giveItemAllOnlineChr(submitData.getId(), Short.parseShort(submitData.getQuantity().toString()));
                break;
            case 6: // equip
                giveEquipAllOnlineChr(submitData);
                break;
            // 全服没有设置倍率的操作
            // case 7: // expRate
            // case 8: // mesosRate
            // case 9: // dropRate
            // case 10: // bossRate
            //     String rateType = switch (submitData.getType()) {
            //         case 7 -> "Exp";
            //         case 8 -> "Mesos";
            //         case 9 -> "Drop";
            //         case 10 -> "Boss";
            //         default -> "None";
            //     };
            //     giveRateAllOnlineChr(rateType, submitData.getRate());
            //     break;
        }
    }

    private void giveChr(GiveResourceReqDTO submitData) {
        Integer wId = submitData.getWorldId();
        Integer cId = submitData.getPlayerId();
        if (wId == null || wId < 0 || cId == null || cId < 1) {
            throw new BizException("大区 ID 或者 玩家 ID 不正确");
        }

        switch (submitData.getType()) {
            case 0: // nxCredit 点券
            case 1: // nxPrepaid 信用点券
            case 2: // maplePoint 抵用券
                int cashType = switch (submitData.getType()) {
                    case 1 -> CashShop.NX_PREPAID;
                    case 2 -> CashShop.MAPLE_POINT;
                    default -> CashShop.NX_CREDIT;
                };
                giveNxChr(wId, cId, submitData.getQuantity(), cashType);
                break;
            case 3: // mesos
                giveMesosChr(wId, cId, submitData.getQuantity());
                break;
            case 4: // exp
                giveExpChr(wId, cId, submitData.getQuantity());
                break;
            case 5: // item
                giveItemChr(wId, cId, submitData.getId(), Short.parseShort(submitData.getQuantity().toString()));
                break;
            case 6: // equip
                giveEquipChr(wId, cId, submitData);
                break;
            case 7: // expRate
            case 8: // mesosRate
            case 9: // dropRate
            case 10: // bossRate
                String rateType = switch (submitData.getType()) {
                    case 7 -> "expRate";
                    case 8 -> "mesoRate";
                    case 9 -> "dropRate";
                    case 10 -> "bossRate";
                    default -> "None";
                };
                giveRateChr(wId, cId, rateType, submitData.getRate());
                break;
        }
    }

    private void giveNxAllOnlineChr(int quantity, int type) {
        Server.getInstance().getWorlds().forEach(world -> world.getPlayerStorage().getAllCharacters().forEach(chr -> {
            chr.getCashShop().gainCash(type, quantity);
            chr.message("管理员给全服发放了 " + quantity + " " + getCashTypeName(type));
        }));
        log.info("管理员在后台给全服发放了 {} {}", quantity, getCashTypeName(type));
    }

    private void giveNxChr(int wId, int cId, int quantity, int type) {
        Character chr = Server.getInstance()
                .getWorlds().get(wId)
                .getPlayerStorage().getCharacterById(cId);

        if (chr == null) throw new BizException("玩家已离线");
        chr.getCashShop().gainCash(type, quantity);
        chr.message("管理员给你发放了 " + quantity + " " + getCashTypeName(type));
        log.info("管理员在后台给玩家 [{}] {} 发放了 {} {}", chr.getId(), chr.getName(), quantity, getCashTypeName(type));
    }

    private String getCashTypeName(int type) {
        return switch (type) {
            case 1 -> "点券";
            case 2 -> "抵用券";
            default -> "信用点券";
        };
    }

    private void giveMesosAllOnlineChr(int quantity) {
        Server.getInstance().getWorlds().forEach(world -> world.getPlayerStorage().getAllCharacters().forEach(chr -> {
            chr.gainMeso(quantity);
            chr.message("管理员给全服发放了 " + quantity + " 金币");
        }));
        log.info("管理员在后台给全服发放了 {} 金币", quantity);
    }

    private void giveMesosChr(int wId, int cId, int quantity) {
        Character chr = Server.getInstance()
                .getWorlds().get(wId)
                .getPlayerStorage().getCharacterById(cId);

        if (chr == null) throw new BizException("玩家已离线");

        chr.gainMeso(quantity);
        chr.message("管理员给你发放了 " + quantity + " 金币");
        log.info("管理员在后台给玩家 [{}] {} 发放了 {} 金币", chr.getId(), chr.getName(), quantity);
    }

    private void giveExpAllOnlineChr(int quantity) {
        Server.getInstance().getWorlds().forEach(world -> world.getPlayerStorage().getAllCharacters().forEach(chr -> {
            chr.gainExp(quantity);
            chr.message("管理员给全服发放了 " + quantity + " 经验");
        }));
        log.info("管理员在后台给全服发放了 {} 经验", quantity);
    }

    private void giveExpChr(int wId, int cId, int quantity) {
        Character chr = Server.getInstance()
                .getWorlds().get(wId)
                .getPlayerStorage().getCharacterById(cId);

        if (chr == null) throw new BizException("玩家已离线");

        chr.gainExp(quantity);
        chr.message("管理员给你发放了 " + quantity + " 经验");
        log.info("管理员在后台给玩家 [{}] {} 发放了 {} 经验", chr.getId(), chr.getName(), quantity);
    }

    private void giveItemAllOnlineChr(int itemId, short quantity) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        String itemName = ii.getName(itemId);
        if (itemName == null) {
            throw new BizException("物品不存在");
        }

        boolean isPet = ItemConstants.isPet(itemId);

        long expiration;
        int petId;
        if (isPet) {
            long days = Math.max(1, quantity);
            expiration = System.currentTimeMillis() + DAYS.toMillis(days);
            petId = Pet.createPet(itemId);
        } else {
            expiration = 0;
            petId = 0;
        }

        Server.getInstance().getWorlds().forEach(world -> world.getPlayerStorage().getAllCharacters().forEach(chr -> {
            if (isPet) {
                InventoryManipulator.addById(chr.getClient(), itemId, quantity, "WAdmin", petId, expiration);
                chr.message("管理员给全服发放了 " + quantity + " 天 " + itemName);
            } else {
                InventoryManipulator.addById(chr.getClient(), itemId, quantity, "WAdmin", -1, (short) 0, -1);
                chr.message("管理员给全服发放了 " + quantity + " 个 " + itemName);
            }
        }));

        if (isPet) {
            log.info("管理员在后台给全服发放了 {} 天 [{}]{}", quantity, itemId, itemName);
        } else {
            log.info("管理员在后台给全服发放了 {} 个 [{}]{}", quantity, itemId, itemName);
        }

    }

    private void giveItemChr(int wId, int cId, int itemId, short quantity) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        String itemName = ii.getName(itemId);
        if (itemName == null) {
            throw new BizException("物品不存在");
        }

        boolean isPet = ItemConstants.isPet(itemId);

        long expiration = 0;
        int petId = 0;
        if (isPet) {
            long days = Math.max(1, quantity);
            expiration = System.currentTimeMillis() + DAYS.toMillis(days);
            petId = Pet.createPet(itemId);
        }

        Character chr = Server.getInstance()
                .getWorlds().get(wId)
                .getPlayerStorage().getCharacterById(cId);

        if (chr == null) throw new BizException("玩家已离线");

        if (isPet) {
            InventoryManipulator.addById(chr.getClient(), itemId, quantity, "WAdmin", petId, expiration);
            chr.message("管理员给你发放了 " + quantity + " 天 " + itemName);
        } else {
            InventoryManipulator.addById(chr.getClient(), itemId, quantity, "WAdmin", -1, (short) 0, -1);
            chr.message("管理员给你发放了 " + quantity + " 个 " + itemName);
        }

        if (isPet) {
            log.info("管理员在后台给玩家 [{}] {} 发放了 {} 天 [{}] {}}", chr.getId(), chr.getName(), quantity, itemId, itemName);
        } else {
            log.info("管理员在后台给玩家 [{}] {} 发放了 {} 个 [{}] {}}", chr.getId(), chr.getName(), quantity, itemId, itemName);
        }
    }

    private void giveEquipAllOnlineChr(GiveResourceReqDTO submitData) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        String itemName = ii.getName(submitData.getId());
        if (ii.getEquipById(submitData.getId()) == null || itemName == null) {
            throw new BizException("装备不存在");
        }
        Server.getInstance().getWorlds().forEach(world -> world.getPlayerStorage().getAllCharacters().forEach(chr -> {
            chr.gainEquip(
                    submitData.getId(),
                    submitData.getStr(),
                    submitData.getDex(),
                    submitData.get_int(),
                    submitData.getLuk(),
                    submitData.getHp(),
                    submitData.getMp(),
                    submitData.getPAtk(),
                    submitData.getMAtk(),
                    submitData.getPDef(),
                    submitData.getMDef(),
                    submitData.getAcc(),
                    submitData.getAvoid(),
                    submitData.getHands(),
                    submitData.getSpeed(),
                    submitData.getJump(),
                    submitData.getUpgradeSlot(),
                    submitData.getExpire()
            );
            chr.message("管理员给全服发放了自定义装备 [" + submitData.getId().toString() + "] " + itemName);
        }));
        log.info("管理员在后台给全服发放了自定义装备 [{}] {} 力量：{} 敏捷：{} 智力：{} 运气：{} HP：{} MP：{} 物攻：{} 魔攻：{} 物防：{} 魔防：{} 命中：{} 回避：{} 手技：{} 移速：{} 跳跃：{} 升级次数：{} 有效期：{} 分钟",
                submitData.getId(),
                itemName,
                submitData.getStr(),
                submitData.getDex(),
                submitData.get_int(),
                submitData.getLuk(),
                submitData.getHp(),
                submitData.getMp(),
                submitData.getPAtk(),
                submitData.getMAtk(),
                submitData.getPDef(),
                submitData.getMDef(),
                submitData.getAcc(),
                submitData.getAvoid(),
                submitData.getHands(),
                submitData.getSpeed(),
                submitData.getJump(),
                submitData.getUpgradeSlot(),
                submitData.getExpire()
        );
    }

    private void giveEquipChr(int wId, int cId, GiveResourceReqDTO submitData) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        String itemName = ii.getName(submitData.getId());
        if (ii.getEquipById(submitData.getId()) == null || itemName == null) {
            throw new BizException("装备不存在");
        }
        Character chr = Server.getInstance().getWorld(wId).getPlayerStorage().getCharacterById(cId);
        if (chr == null) throw new BizException("玩家已离线");
        chr.gainEquip(
                submitData.getId(),
                submitData.getStr(),
                submitData.getDex(),
                submitData.get_int(),
                submitData.getLuk(),
                submitData.getHp(),
                submitData.getMp(),
                submitData.getPAtk(),
                submitData.getMAtk(),
                submitData.getPDef(),
                submitData.getMDef(),
                submitData.getAcc(),
                submitData.getAvoid(),
                submitData.getHands(),
                submitData.getSpeed(),
                submitData.getJump(),
                submitData.getUpgradeSlot(),
                submitData.getExpire()
        );
        chr.message("管理员给玩家 [" + cId + "] " + chr.getName() + " 发放了自定义装备 [" + submitData.getId().toString() + "] " + itemName);
        log.info("管理员在后台给玩家 [{}] {} 发放了自定义装备 [{}] {} 力量：{} 敏捷：{} 智力：{} 运气：{} HP：{} MP：{} 物攻：{} 魔攻：{} 物防：{} 魔防：{} 命中：{} 回避：{} 手技：{} 移速：{} 跳跃：{} 升级次数：{} 有效期：{} 分钟",
                cId,
                chr.getName(),
                submitData.getId(),
                itemName,
                submitData.getStr(),
                submitData.getDex(),
                submitData.get_int(),
                submitData.getLuk(),
                submitData.getHp(),
                submitData.getMp(),
                submitData.getPAtk(),
                submitData.getMAtk(),
                submitData.getPDef(),
                submitData.getMDef(),
                submitData.getAcc(),
                submitData.getAvoid(),
                submitData.getHands(),
                submitData.getSpeed(),
                submitData.getJump(),
                submitData.getUpgradeSlot(),
                submitData.getExpire()
        );
    }

    private void giveRateChr(int wId, int cId, String type, float rate) {
        Character chr = Server.getInstance().getWorld(wId).getPlayerStorage().getCharacterById(cId);
        if (chr == null) throw new BizException("玩家已离线");

        ExtendValueDO data = ExtendValueDO.builder()
                .extendId(String.valueOf(chr.getId()))
                .extendType("21")
                .extendName(type)
                .extendValue(String.valueOf(rate))
                .build();
        characterService.updateRate(data);
        
        chr.message("管理员将你的 " + type + " 调整为：" + rate);
        log.info("管理员在后台将玩家 [{}] {} 的 {} 调整为：{}", chr.getId(), chr.getName(), type, rate);
    }
}
