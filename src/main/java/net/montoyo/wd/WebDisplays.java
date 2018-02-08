/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.montoyo.wd.block.BlockKeyboardRight;
import net.montoyo.wd.block.BlockPeripheral;
import net.montoyo.wd.block.BlockScreen;
import net.montoyo.wd.core.*;
import net.montoyo.wd.entity.TileEntityScreen;
import net.montoyo.wd.item.*;
import net.montoyo.wd.miniserv.server.Server;
import net.montoyo.wd.net.client.CMessageServerInfo;
import net.montoyo.wd.net.Messages;
import net.montoyo.wd.utilities.Log;
import net.montoyo.wd.utilities.Util;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Mod(modid = "webdisplays", version = WebDisplays.MOD_VERSION, dependencies = "required-after:mcef;")
public class WebDisplays {

    public static final String MOD_VERSION = "1.0";

    @Mod.Instance(owner = "webdisplays")
    public static WebDisplays INSTANCE;

    @SidedProxy(serverSide = "net.montoyo.wd.SharedProxy", clientSide = "net.montoyo.wd.client.ClientProxy")
    public static SharedProxy PROXY;

    public static SimpleNetworkWrapper NET_HANDLER;
    public static WDCreativeTab CREATIVE_TAB;
    public static final ResourceLocation ADV_PAD_BREAK = new ResourceLocation("webdisplays", "webdisplays/pad_break");

    //Blocks
    public BlockScreen blockScreen;
    public BlockPeripheral blockPeripheral;
    public BlockKeyboardRight blockKbRight;

    //Items
    public ItemScreenConfigurator itemScreenCfg;
    public ItemOwnershipThief itemOwnerThief;
    public ItemLinker itemLinker;
    public ItemMinePad2 itemMinePad;
    public ItemUpgrade itemUpgrade;
    public ItemLaserPointer itemLaserPointer;
    public ItemCraftComponent itemCraftComp;
    public ItemMulti itemAdvIcon;

    //Sounds
    public SoundEvent soundTyping;
    public SoundEvent soundUpgradeAdd;
    public SoundEvent soundUpgradeDel;
    public SoundEvent soundScreenCfg;
    public SoundEvent soundServer;

    //Criterions
    public Criterion criterionPadBreak;
    public Criterion criterionUpgradeScreen;
    public Criterion criterionLinkPeripheral;
    public Criterion criterionKeyboardCat;

    //Config
    public static final double PAD_RATIO = 59.0 / 30.0;
    public String homePage = "mod://webdisplays/main.html"; //TODO: Read from config
    public double padResX;
    public double padResY;
    private int lastPadId = 0;
    public boolean doHardRecipe = true;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent ev) {
        CREATIVE_TAB = new WDCreativeTab();

        //Criterions
        criterionPadBreak = new Criterion("pad_break");
        criterionUpgradeScreen = new Criterion("upgrade_screen");
        criterionLinkPeripheral = new Criterion("link_peripheral");
        criterionKeyboardCat = new Criterion("keyboard_cat");
        registerTrigger(criterionPadBreak, criterionUpgradeScreen, criterionLinkPeripheral, criterionKeyboardCat);

        //Read configuration TODO
        final int padHeight = 480;
        padResY = (double) padHeight;
        padResX = padResY * PAD_RATIO;

        //Init blocks
        blockScreen = new BlockScreen();
        blockScreen.makeItemBlock();

        blockPeripheral = new BlockPeripheral();
        blockPeripheral.makeItemBlock();

        blockKbRight = new BlockKeyboardRight();

        //Init items
        itemScreenCfg = new ItemScreenConfigurator();
        itemOwnerThief = new ItemOwnershipThief();
        itemLinker = new ItemLinker();
        itemMinePad = new ItemMinePad2();
        itemUpgrade = new ItemUpgrade();
        itemLaserPointer = new ItemLaserPointer();
        itemCraftComp = new ItemCraftComponent();

        itemAdvIcon = new ItemMulti(AdvancementIcon.class);
        itemAdvIcon.setUnlocalizedName("webdisplays.advicon");
        itemAdvIcon.setRegistryName("advicon");

        PROXY.preInit();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent ev) {
        //Register tile entities
        GameRegistry.registerTileEntity(TileEntityScreen.class, "webdisplays:screen");
        for(DefaultPeripheral dp: DefaultPeripheral.values()) {
            if(dp.getTEClass() != null)
                GameRegistry.registerTileEntity(dp.getTEClass(), "webdisplays:" + dp.getName());
        }

        //Other things
        PROXY.init();
        NET_HANDLER = NetworkRegistry.INSTANCE.newSimpleChannel("webdisplays");
        Messages.registerAll(NET_HANDLER);
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent ev) {
        PROXY.postInit();
    }

    @SubscribeEvent
    public void onRegisterBlocks(RegistryEvent.Register<Block> ev) {
        ev.getRegistry().registerAll(blockScreen, blockPeripheral, blockKbRight);
    }

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> ev) {
        ev.getRegistry().registerAll(blockScreen.getItem(), blockPeripheral.getItem());
        ev.getRegistry().registerAll(itemScreenCfg, itemOwnerThief, itemLinker, itemMinePad, itemUpgrade, itemLaserPointer, itemCraftComp, itemAdvIcon);
    }

    @SubscribeEvent
    public void onRegisterSounds(RegistryEvent.Register<SoundEvent> ev) {
        soundTyping = registerSound(ev, "keyboardType");
        soundUpgradeAdd = registerSound(ev, "upgradeAdd");
        soundUpgradeDel = registerSound(ev, "upgradeDel");
        soundScreenCfg = registerSound(ev, "screencfgOpen");
        soundServer = registerSound(ev, "server");
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load ev) {
        if(ev.getWorld().isRemote || ev.getWorld().provider.getDimension() != 0)
            return;

        File worldDir = ev.getWorld().getSaveHandler().getWorldDirectory();
        File f = new File(worldDir, "wd_next.txt");

        if(f.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String idx = br.readLine();
                Util.silentClose(br);

                if(idx == null)
                    throw new RuntimeException("Seems like the file is empty (1)");

                idx = idx.trim();
                if(idx.isEmpty())
                    throw new RuntimeException("Seems like the file is empty (2)");

                lastPadId = Integer.parseInt(idx); //This will throw NumberFormatException if it goes wrong
            } catch(Throwable t) {
                Log.warningEx("Could not read last minePad ID from %s. I'm afraid this might break all minePads.", t, f.getAbsolutePath());
            }
        }

        Server sv = Server.getInstance();
        sv.setDirectory(new File(worldDir, "wd_filehost"));
        sv.start(); //TODO: Configure port
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save ev) {
        if(ev.getWorld().isRemote || ev.getWorld().provider.getDimension() != 0)
            return;

        File f = new File(ev.getWorld().getSaveHandler().getWorldDirectory(), "wd_next.txt");

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(f));
            bw.write("" + lastPadId + "\n");
            Util.silentClose(bw);
        } catch(Throwable t) {
            Log.warningEx("Could not save last minePad ID (%d) to %s. I'm afraid this might break all minePads.", t, lastPadId, f.getAbsolutePath());
        }
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent ev) {
        if(!ev.getEntityItem().world.isRemote) {
            ItemStack is = ev.getEntityItem().getItem();

            if(is.getItem() == itemMinePad) {
                NBTTagCompound tag = is.getTagCompound();

                if(tag == null) {
                    tag = new NBTTagCompound();
                    is.setTagCompound(tag);
                }

                UUID thrower = ev.getPlayer().getGameProfile().getId();
                tag.setLong("ThrowerMSB", thrower.getMostSignificantBits());
                tag.setLong("ThrowerLSB", thrower.getLeastSignificantBits());
                tag.setDouble("ThrowHeight", ev.getPlayer().posY + ev.getPlayer().getEyeHeight());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerCraft(PlayerEvent.ItemCraftedEvent ev) {
        if(doHardRecipe && ev.crafting.getItem() == itemCraftComp && ev.crafting.getMetadata() == CraftComponent.EXTENSION_CARD.ordinal()) {
            if((ev.player instanceof EntityPlayerMP && !hasPlayerAdvancement((EntityPlayerMP) ev.player, ADV_PAD_BREAK)) || PROXY.hasClientPlayerAdvancement(ADV_PAD_BREAK) != HasAdvancement.YES) {
                ev.crafting.setItemDamage(CraftComponent.BAD_EXTENSION_CARD.ordinal());

                if(!ev.player.world.isRemote)
                    ev.player.world.playSound(null, ev.player.posX, ev.player.posY, ev.player.posZ, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    @Mod.EventHandler
    public void onServerStop(FMLServerStoppingEvent ev) {
        Server.getInstance().stopServer();
    }

    @SubscribeEvent
    public void onLogIn(PlayerEvent.PlayerLoggedInEvent ev) {
        if(!ev.player.world.isRemote && ev.player instanceof EntityPlayerMP)
            WebDisplays.NET_HANDLER.sendTo(new CMessageServerInfo(25566), (EntityPlayerMP) ev.player); //TODO: Port config
    }

    @SubscribeEvent
    public void onLogOut(PlayerEvent.PlayerLoggedOutEvent ev) {
        if(!ev.player.world.isRemote)
            Server.getInstance().getClientManager().revokeClientKey(ev.player.getGameProfile().getId());
    }

    private boolean hasPlayerAdvancement(EntityPlayerMP ply, ResourceLocation rl) {
        MinecraftServer server = PROXY.getServer();
        if(server == null)
            return false;

        Advancement adv = server.getAdvancementManager().getAdvancement(rl);
        return adv != null && ply.getAdvancements().getProgress(adv).isDone();
    }

    public static int getNextAvailablePadID() {
        return INSTANCE.lastPadId++;
    }

    private static SoundEvent registerSound(RegistryEvent.Register<SoundEvent> ev, String resName) {
        ResourceLocation resLoc = new ResourceLocation("webdisplays", resName);
        SoundEvent ret = new SoundEvent(resLoc);
        ret.setRegistryName(resLoc);

        ev.getRegistry().register(ret);
        return ret;
    }

    private static void registerTrigger(Criterion ... criteria) {
        Method[] methods = CriteriaTriggers.class.getDeclaredMethods();
        Optional<Method> register = Arrays.stream(methods).filter(m -> Modifier.isPrivate(m.getModifiers()) && Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 1).findAny();
        if(!register.isPresent())
            throw new RuntimeException("Could not register advancement criterion triggers");

        try {
            Method m = register.get();
            m.setAccessible(true);

            for(Criterion c: criteria)
                m.invoke(null, c);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

}

