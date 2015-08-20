package dorfprojekt;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.io.Files;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ForgeEventHandler {

	@SubscribeEvent
	public void onWorldSave(WorldEvent.Save event)
	{
		if(event.world instanceof WorldServer && event.world.provider.dimensionId == 0)
		{
			try
			{
				File file;
				if(event.world.provider.getSaveFolder() == null)
				{
					file = new File(Util.getSaveDirectory(MinecraftServer.getServer().getFolderName()).getAbsolutePath() + File.separator + "dorfprojekt.dat");
				}
				else
				{
					file = new File(Util.getSaveDirectory(MinecraftServer.getServer().getFolderName() + File.separator + event.world.provider.getSaveFolder()).getAbsolutePath() + File.separator + "dorfprojekt.dat");
				}

				NBTTagCompound mainTag = new NBTTagCompound();

				NBTTagCompound teams = new NBTTagCompound();
				Team.writeToNBT(teams);
				mainTag.setTag("teams", teams);

				StreamHelper.newOutputStream();
				StreamHelper.writeNBTTagCompound(mainTag);
				Files.write(StreamHelper.getByteArray(), file);
			}
			catch(IOException ex)
			{
				ex.printStackTrace();
			}

		}
	}

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event)
	{
		if(event.world instanceof WorldServer && event.world.provider.dimensionId == 0)
		{
			try
			{
				File file;
				if(event.world.provider.getSaveFolder() == null)
				{
					file = new File(Util.getSaveDirectory(MinecraftServer.getServer().getFolderName()).getAbsolutePath() + File.separator + "dorfprojekt.dat");
				}
				else
				{
					file = new File(Util.getSaveDirectory(MinecraftServer.getServer().getFolderName() + File.separator + event.world.provider.getSaveFolder()).getAbsolutePath() + File.separator + "dorfprojekt.dat");
				}

				if(file.exists())
				{
					StreamHelper.bindInputStream(Files.toByteArray(file));
					NBTTagCompound mainTag = StreamHelper.readNBTTagCompound();

					NBTTagCompound teams = mainTag.getCompoundTag("teams");
					Team.readFromNBT(teams);
				}

			}
			catch(IOException ex)
			{
				ex.printStackTrace();
			}
		}
	}

	@SubscribeEvent
	public void onBlockBreak(BlockEvent.BreakEvent event)
	{
		if(!destroyBlock(event.world, event.x, event.y, event.z, event.getPlayer()))
		{
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onExplosionDetonate(ExplosionEvent.Detonate event)
	{
		Iterator<ChunkPosition> iter = event.getAffectedBlocks().iterator();
		while(iter.hasNext())
		{
			ChunkPosition pos = iter.next();
			if(!destroyBlock(event.world, pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ, null))
			{
				iter.remove();
			}
		}
	}

	@SubscribeEvent
	public void onBlockPlace(BlockEvent.PlaceEvent event)
	{
		Team team = Team.getTeamForCoords(event.x, event.z);
		if(team != null && !team.isPlayerInTeam(event.player))
		{
			if(team.isAttackable())
			{
				if(team.blocksPlacedInArea.size() >= Dorfprojekt.maxPlaceableBlocks)
				{
					Util.sendTranslatedChat(event.player, "dorfprojekt.noMoreBlocks");
					event.setCanceled(true);
				}
				else
				{
					team.blocksPlacedInArea.add(new Coords4(event.world, event.x, event.y, event.z));
				}
			}
			else
			{
				event.setCanceled(true);
			}
		}
	}

	public boolean destroyBlock(World world, int x, int y, int z, EntityPlayer player)
	{
		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileCrownPodest)
		{
			TileCrownPodest tcp = (TileCrownPodest) te;
			if(tcp.getTeam() != Team.getTeamForPlayer(player))
			{
				return false;
			}
		}
		
		Team team = Team.getTeamForCoords(x, z);
		if(team != null)
		{
			if(!team.isAttackable() && (player == null || !team.isPlayerInTeam(player)))
			{
				return false;
			}
			else
			{
				Coords4 coords = new Coords4(world, x, y, z);
				team.blocksPlacedInArea.remove(coords);			
				
				if(FMLCommonHandler.instance().getEffectiveSide().isServer())
				{
					if(team.isOnOuterBorder(x, z))
					{
						BlockBorder.scheduledBorders.put(new Coords4(world, x, y, z), 0);
					}
					else if(team.isOnInnerBorder(x, z))
					{
						BlockBorder.scheduledBorders.put(new Coords4(world, x, y, z), 1);
					}
				}
			}
		}
		return true;
	}

	@SubscribeEvent
	public void onPlayerBreakSpeed(PlayerEvent.BreakSpeed event)
	{
		Team team;
		if(Dorfprojekt.slowdownInOuterBorders)
		{
			team = Team.getTeamForCoords(event.x, event.z);
		}
		else
		{
			team = Team.getTeamForCoordsWithInnerBorders(event.x, event.z);
		}
		if(team != null)
		{
			if(team.isPlayerInTeam(event.entityPlayer))
			{
				if(team.isCurrentlyAttacked(event.entityPlayer.worldObj))
				{
					event.newSpeed *= Dorfprojekt.breakSpeedMultiplierFriendly;
				}
			}
			else
			{
				event.newSpeed *= Dorfprojekt.breakSpeedMultiplierEnemy;
			}
		}
	}

	@SubscribeEvent
	public void onLivingUpdate(LivingEvent.LivingUpdateEvent event)
	{
		if(event.entityLiving instanceof EntityPlayer)
		{
			EntityPlayer player = (EntityPlayer) event.entityLiving;
			Team team = Team.getTeamForCoords(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ));
			if(!player.capabilities.isCreativeMode && team != null && !team.isAttackable() && team != Team.getTeamForPlayer(player))
			{
				team.teleportEntityToBorders(player);
			}
			
			
		}
	}
	
	@SubscribeEvent
	public void onItemExpire(ItemExpireEvent event)
	{
		if(event.entityItem.getEntityItem().getItem() == Dorfprojekt.crownItem)
		{
			Team team = ItemCrown.getTeam(event.entityItem.getEntityItem());
			if(team != null)
			{
				System.out.println("Crown expired: " + event.entityItem);
				team.respawnCrown();
				event.entityItem.setDead();
			}
		}
	}
	
	@SubscribeEvent	
	public void onEntityJoinWorld(EntityJoinWorldEvent event)
	{
		if(event.entity instanceof EntityItem)
		{
			EntityItem eitem = (EntityItem) event.entity;
			if(eitem.getEntityItem().getItem() == Dorfprojekt.crownItem)
			{
				NBTTagCompound tag = new NBTTagCompound();
				eitem.writeToNBT(tag);
				tag.setBoolean("Invulnerable", true);
				eitem.readFromNBT(tag);
				
				eitem.lifespan = Dorfprojekt.crownLifespan;
			}
		}
	}
	
	@SubscribeEvent
	public void onPlayerNameFormat(PlayerEvent.NameFormat event)
	{
		Team team = Team.getTeamForPlayer(event.entityPlayer);
		if(team != null && team.color != null)
		{
			event.displayname = Util.FORMAT_CHAR + EnumChatFormatting.getValueByName(team.color).getFormattingCode() + event.displayname;
			System.out.println(event.displayname);
		}
	}
	
}
