package com.zenith.AIBot;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.zenith.AIBot.network.GuiStatePacket;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.NonNullList;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

import static net.minecraft.network.EnumPacketDirection.SERVERBOUND;

/**
 * Comando principal responsável pelo gerenciamento, spawn e inteligência artificial
 * dos Fake Players customizados (SigmaBots).
 */
public class ComandoFakePlayer extends CommandBase {

    // ==========================================
    // ESTRUTURAS AUXILIARES
    // ==========================================

    /**
     * Nó utilizado pelo algoritmo de pathfinding A* para mapeamento de rotas.
     */
    private static class NodeCaminho {
        BlockPos pos;
        NodeCaminho pai;
        double g;
        double f;

        public NodeCaminho(BlockPos pos, NodeCaminho pai, double g, double h) {
            this.pos = pos;
            this.pai = pai;
            this.g = g;
            this.f = g + h;
        }
    }

    // ==========================================
    // CLASSE CORE: ENTITY PLAYER SIGMA
    // ==========================================

    public static class EntityPlayerSigma extends EntityPlayerMP {

        // Atributos de Controle de Estado e Variáveis Locais
        boolean carregouMemoria = false;
        EntityItem alvoItem = null;
        EntityLivingBase alvoAnimal = null;
        RedeNeural cerebro = new RedeNeural(this.getName());
        List<BlockPos> blocosBlackList = new ArrayList<>();
        List<BlockPos> caminhoAtual = new ArrayList<>();
        EntityLivingBase alvoAtual = null;
        BlockPos blocoSendoMinerado = null;
        String estadoAtual = "Exploring";
        EntityItem alvoItemChao = null;

        int tickComer = 32;
        int indexCaminho = 0;
        int cooldownRadar = 5;
        int tickQuebrarMadeira = 80;
        int tickRenascer = 40;
        float progressoMineracao = 0.0F;
        double vidaAntes = -1;
        int mobsAntes = 0;
        int nivelArmaduraAntes = 0;
        int madeirasAntes = 0;
        double fomeAntes = -1;
        int pedrasAntes = 0;
        int ultimaAcao = 0;
        int cooldownDecisaoTick = 0;

        boolean renascendo = false;
        boolean parar;
        boolean temArmaduraAntes = false;

        List<BlockPos> blocosDaTorre = new ArrayList<>();


        // Lista estática de minérios visados pela IA de mineração
        public static final List<Block> MINERIOS = new ArrayList<>(Arrays.asList(
                Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.LAPIS_ORE,
                Blocks.REDSTONE_ORE, Blocks.LIT_REDSTONE_ORE, Blocks.DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.QUARTZ_ORE
        ));

        public static final List<ItemArmor> ARMADURAS = new ArrayList<>(Arrays.asList(
                Items.LEATHER_BOOTS, Items.LEATHER_LEGGINGS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET,
                Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET,
                Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET
        ));

        public EntityPlayerSigma(MinecraftServer server, WorldServer worldIn, GameProfile profile, PlayerInteractionManager interactionManagerIn) {
            super(server, worldIn, profile, interactionManagerIn);
            this.stepHeight = 0.6F;
            this.interactionManager.setGameType(GameType.SURVIVAL);
        }

        // ------------------------------------------
        // SISTEMA DE MOVIMENTAÇÃO E PATHFINDING (A*)
        // ------------------------------------------

        /**
         * Executa o algoritmo A* para calcular o caminho ideal até um bloco ou entidade alvo.
         */
        private void gerarCaminhoAteAlvo(BlockPos bloco2) {
            if (this.alvoAtual == null && bloco2 == null) return;

            BlockPos inicio = new BlockPos(this.posX, this.posY, this.posZ);
            BlockPos fim = (bloco2 != null) ? new BlockPos(bloco2.getX(), bloco2.getY(), bloco2.getZ()) : new BlockPos(alvoAtual.posX, alvoAtual.posY, alvoAtual.posZ);

            if (this.world.getBlockState(fim).getMaterial().isSolid()) {
                BlockPos fimUp = fim.up();
                if (!this.world.getBlockState(fimUp).getMaterial().isSolid()) {
                    fim = fimUp;
                } else {
                    // Tenta um dos lados (norte, sul, leste, oeste)
                    for (EnumFacing face : EnumFacing.HORIZONTALS) {
                        BlockPos lado = fim.offset(face);
                        if (!this.world.getBlockState(lado).getMaterial().isSolid() &&
                                !this.world.getBlockState(lado.up()).getMaterial().isSolid()) {
                            fim = lado;
                            break;
                        }
                    }
                }
            }

            if (inicio.distanceSq(fim) <= 4) {
                this.caminhoAtual.clear();
                this.indexCaminho = 0;
                return;
            }

            PriorityQueue<NodeCaminho> vizinhos = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
            Set<BlockPos> passados = new HashSet<>();
            int limiteCalc = 2000;

            vizinhos.add(new NodeCaminho(inicio, null, 0, inicio.distanceSq(fim)));
            passados.add(inicio);

            while (!vizinhos.isEmpty() && limiteCalc-- > 0) {
                NodeCaminho atual = vizinhos.poll();
                if (atual.pos.distanceSq(fim) <= 4) {
                    this.caminhoAtual.clear();
                    NodeCaminho temp = atual;
                    while (temp != null) {
                        this.caminhoAtual.add(0, temp.pos);
                        temp = temp.pai;
                    }
                    this.indexCaminho = 0;
                    return;
                }
                passados.add(atual.pos);

                BlockPos[] direcoes = {atual.pos.north(), atual.pos.south(), atual.pos.east(), atual.pos.west()};

                for (BlockPos vizinho : direcoes) {
                    BlockPos vizinhoValido = chegarPerigo(atual.pos, vizinho);

                    if (vizinhoValido != null && !passados.contains(vizinhoValido)) {
                        double Gplus = atual.g + 1.0;
                        int vizinhoX = vizinhoValido.getX();
                        int vizinhoY = vizinhoValido.getY();
                        int vizinhoZ = vizinhoValido.getZ();
                        double Hplus = ((vizinhoX - fim.getX()) * (vizinhoX - fim.getX())) + ((vizinhoY - fim.getY()) * (vizinhoY - fim.getY()) + ((vizinhoZ - fim.getZ()) * (vizinhoZ - fim.getZ())));

                        boolean pular = false;
                        for (NodeCaminho bloco : vizinhos) {
                            if (bloco.pos.equals(vizinhoValido) && bloco.g <= Gplus) {
                                pular = true;
                                break;
                            }
                        }
                        if (!pular) {
                            passados.add(vizinhoValido);
                            vizinhos.add(new NodeCaminho(vizinhoValido, atual, Gplus, Hplus));
                        }
                    }
                }
            }
        }

        /**\
         * Consome a lista de caminhos gerada e move mecanicamente o bot bloco a bloco.
         */
        public void andarPath(BlockPos bloco) {
            if (alvoAtual == null && bloco == null) return;


            double distSq = (bloco != null) ? this.getDistanceSq(bloco) : this.getDistanceSq(alvoAtual);

            if (caminhoAtual != null && indexCaminho < caminhoAtual.size() && !caminhoAtual.isEmpty()) {
                BlockPos proximopasso = caminhoAtual.get(indexCaminho);

                if (this.onGround && proximopasso.getY() > this.posY) {
                    double dx = proximopasso.getX() + 0.5 - this.posX;
                    double dz = proximopasso.getZ() + 0.5 - this.posZ;
                    double distH = MathHelper.sqrt(dx * dx + dz * dz);

                    if (distH > 0.1) {
                        int dirX = (int) Math.signum(dx);
                        int dirZ = (int) Math.signum(dz);

                        BlockPos frente = new BlockPos((int)this.posX + dirX, (int)this.posY, (int)this.posZ + dirZ);
                        IBlockState stateFrente = this.world.getBlockState(frente);

                        if (stateFrente.getMaterial().isSolid() && !this.world.getBlockState(frente.up()).getMaterial().isSolid()) {
                            this.jump();

                            double impulso = 0.15;

                            this.motionX += (dx / distH) * impulso;
                            this.motionZ += (dz / distH) * impulso;
                        }
                    }
                }

                if (quebrarFolhaNoCaminho(proximopasso)) {
                    caminhoAtual.clear();
                    indexCaminho = 0;
                    gerarCaminhoAteAlvo((bloco != null) ? bloco : (alvoAtual != null) ? new BlockPos(alvoAtual.posX, alvoAtual.posY, alvoAtual.posZ) : null);
                    return;
                }

                double tgx = proximopasso.getX() + 0.5;
                double tgy = proximopasso.getY() + 0.5;
                double tgz = proximopasso.getZ() + 0.5;
                BlockPos tgCord = new BlockPos(tgx, tgy, tgz);

                double dx = tgx - this.posX;
                double dz = tgz - this.posZ;
                double dist = MathHelper.sqrt(dx * dx + dz * dz);


                double velocidade = (distSq > 36.0D) ? 0.35 : 0.3;
                this.moverPara(tgx, tgz, velocidade);

                // Olha para a direção da caminhada se o alvo estiver oculto ou inexistente
                if (alvoAtual == null || !this.canEntityBeSeen(alvoAtual)) {
                    olharParaCoordenadas(tgCord);
                }

                double dx2 = tgx - this.posX;
                double dz2 = tgz - this.posZ;

                if (MathHelper.sqrt(dx2 * dx2 + dz2 * dz2) < 0.6D) {
                    indexCaminho++;
                }
            } else {
                if (alvoAtual == null || caminhoAtual.isEmpty() || !this.canEntityBeSeen(alvoAtual)) {
                    resetarOlhar();
                }
                indexCaminho = 0;
                caminhoAtual.clear();
            }
        }

        /**
         * Aplica vetores físicos de velocidade para deslocar horizontalmente o bot.
         */
        public void moverPara(double posX, double posZ, double velocidade) {
            double dx = posX - this.posX;
            double dz = posZ - this.posZ;
            double dist = MathHelper.sqrt(dx * dx + dz * dz);

            if (dist > 0.5D) {
                this.motionX = (dx / dist) * velocidade;
                this.motionZ = (dz / dist) * velocidade;
            }
        }

        /**
         * Varre a topologia física local para prever e contornar perigos ou obstáculos verticais.
         */
        public BlockPos chegarPerigo(BlockPos de, BlockPos para) {
            WorldServer mundo = (WorldServer) this.world;

            IBlockState stateParaDown = mundo.getBlockState(para.down());
            IBlockState statePara = mundo.getBlockState(para);
            IBlockState stateParaUp = mundo.getBlockState(para.up());

            boolean paraLivre = !statePara.getMaterial().isSolid() || (statePara.getBlock() instanceof BlockLog) || (statePara.getBlock() instanceof BlockLeaves);
            boolean paraUpLivre = !stateParaUp.getMaterial().isSolid() || (stateParaUp.getBlock() instanceof BlockLeaves);
            boolean chaoFirme = stateParaDown.getMaterial().isSolid();

            if (!paraLivre && paraUpLivre) {
                BlockPos paraUp2 = para.up(2);
                if (mundo.getBlockState(paraUp2).getMaterial().isSolid()) {
                    return para;
                }
                return para.up();
            }

            if (paraLivre && paraUpLivre && chaoFirme) {
                return para;
            }

            if (paraLivre && paraUpLivre && !chaoFirme) {
                for (int i = 2; i <= 4; i++) {
                    if (mundo.getBlockState(para.down(i)).getMaterial().isSolid()) {
                        return para;
                    }
                }
                return null;
            }

            if (statePara.getBlock() instanceof BlockLeaves && paraUpLivre) {
                return para;
            }

            return null;
        }

        /**
         * Calcula um vetor oposto em relação ao agressor para traçar uma rota geométrica de fuga.
         */
        public BlockPos calcularFuga(EntityLivingBase perigo, int distancia) {
            double direcaoX = this.posX - perigo.posX;
            double direcaoZ = this.posZ - perigo.posZ;

            double lenght = MathHelper.sqrt((direcaoX * direcaoX) + (direcaoZ * direcaoZ));
            if (lenght == 0) {
                direcaoX = this.world.rand.nextDouble() - 0.5;
                direcaoZ = this.world.rand.nextDouble() - 0.5;
                lenght = MathHelper.sqrt((direcaoX * direcaoX) + (direcaoZ * direcaoZ));
            }

            direcaoX /= lenght;
            direcaoZ /= lenght;


            BlockPos destino = new BlockPos(this.posX, this.posY, this.posZ);

            for (int d = distancia; d > 2; d--) {
                int destX = MathHelper.floor(this.posX + direcaoX * distancia);
                int destZ = MathHelper.floor(this.posZ + direcaoZ * distancia);
                int destY = MathHelper.floor(this.posY);

                BlockPos testePos = new BlockPos(destX, destY, destZ);

                if (!this.world.getBlockState(testePos).getMaterial().isSolid()) {
                    destino = testePos;
                    break;
                }
            }

            for (int i = -3; i <= 3; i++) {
                BlockPos check = destino.down(i);
                if (this.world.getBlockState(check.down()).getMaterial().isSolid() && !this.world.getBlockState(check).getMaterial().isSolid() && !this.world.getBlockState(check.up()).getMaterial().isSolid()) return check;
            }
            return destino;
        }
        
        public BlockPos encontrarPedra(int raio) {
            BlockPos melhorPos = null;
            double menorDistancia = Double.MAX_VALUE;
            BlockPos centro = this.getPosition();

            for (int x = -raio; x <= raio; x++) {
                for (int y = -raio; y <= raio; y++) {
                    for (int z = -raio; z <= raio; z++) {
                        BlockPos blocoSendoObservado = centro.add(x, y ,z);
                        IBlockState estadoObservado = this.world.getBlockState(blocoSendoObservado);

                        if (estadoObservado.getBlock() == Blocks.STONE || estadoObservado.getBlock() == Blocks.COBBLESTONE || estadoObservado.getBlock() == Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.GRANITE) || estadoObservado.getBlock() == Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.DIORITE) || estadoObservado.getBlock() == Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.ANDESITE)) {
                            if (isBlocoExposto(blocoSendoObservado)) {
                                double distSq = this.getDistanceSq(blocoSendoObservado);
                                if (distSq < menorDistancia) {
                                    menorDistancia = distSq;
                                    melhorPos = blocoSendoObservado;
                                }
                            }
                        }
                    }
                }
            }
            return melhorPos;
        }
        
        public BlockPos encontrarArvore(int raio) {
            BlockPos melhorPos = null;
            double menorDistancia = Double.MAX_VALUE;
            BlockPos centro = this.getPosition();

            for (int x = -raio; x <= raio; x++) {
                for (int y = -raio; y <= raio; y++) {
                    for (int z = -raio; z <= raio; z++) {
                        BlockPos blocoSendoObservado = centro.add(x, y ,z);
                        IBlockState estadoObservado = this.world.getBlockState(blocoSendoObservado);

                        if (estadoObservado.getBlock() instanceof BlockLog) {
                            boolean temFolha = false;

                            if (blocosBlackList.contains(blocoSendoObservado)) {
                                continue;
                            }

                            for (int x2 = -2; x2 <= 2; x2++) {
                                for (int y2 = -2; y2 <= 6; y2++) {
                                    for (int z2 = -2; z2 <= 2; z2++) {
                                        BlockPos possivelFolha = blocoSendoObservado.add(x2, y2, z2);
                                        IBlockState estadoPossivelFolha = this.world.getBlockState(possivelFolha);

                                        if (estadoPossivelFolha.getBlock() instanceof BlockLeaves) {
                                            temFolha = true;
                                        }
                                    }
                                }
                            }

                            if (temFolha) {
                                double distSq = this.getDistanceSq(blocoSendoObservado);
                                if (distSq < menorDistancia) {
                                    menorDistancia = distSq;
                                    melhorPos = blocoSendoObservado;
                                }
                            }
                        }
                    }
                }
            }
            if (blocosBlackList.contains(melhorPos)) {
                return null;
            }
            return melhorPos;
        }

        public boolean quebrarFolhaNoCaminho(BlockPos destino) {
            int dx = destino.getX() - (int) this.posX;
            int dz = destino.getZ() - (int) this.posZ;

            int dirX = (int)Math.signum(dx);
            int dirZ = (int)Math.signum(dz);

            if (dirX == 0 && dirZ == 0) return false;

            BlockPos frente = new BlockPos((int) this.posX + dirX, (int) this.posY + 1, (int) this.posZ + dirZ);
            IBlockState estado = this.world.getBlockState(frente);
            if (estado.getBlock() instanceof BlockLeaves) {
                this.swingArm(EnumHand.MAIN_HAND);
                this.interactionManager.tryHarvestBlock(frente);
                this.world.sendBlockBreakProgress(this.getEntityId(), frente, -1);
                return true;
            }

            return false;
        }

        public void quebrarBloco(BlockPos bloco) {
            IBlockState estadoBloco = this.world.getBlockState(bloco);

            if (this.ticksExisted % 4 == 0) {
                this.swingArm(EnumHand.MAIN_HAND);
            }

            if (bloco.getY() - this.getPosition().getY() >= 5) {
                if (this.onGround) {
                    this.jump();
                }
            }

            float danoPorTick = estadoBloco.getPlayerRelativeBlockHardness(this, this.world, bloco);
            progressoMineracao += danoPorTick;
            blocoSendoMinerado = bloco;

            int rachadura = (int) (progressoMineracao * 10.0F);
            this.world.sendBlockBreakProgress(this.getEntityId(), bloco, rachadura);

            if (progressoMineracao >= 1.0F) {
                this.interactionManager.tryHarvestBlock(bloco);
                this.world.sendBlockBreakProgress(this.getEntityId(), bloco, -1);

                progressoMineracao = 0.0F;
                blocoSendoMinerado = null;
            }
        }

        // ------------------------------------------
        // SISTEMA DE ROTAÇÃO DOS OLHOS E VISÃO (LOOK)
        // ------------------------------------------

        /**
         * Gira a cabeça do bot suavemente em incrementos limitados em direção a coordenadas dinâmicas.
         */
        public void mirarSuave(double targetX, double targetY, double targetZ) {
            double dX = targetX - this.posX;
            double dY = targetY - (this.posY + this.getEyeHeight());
            double dZ = targetZ - this.posZ;
            double distHorizontal = MathHelper.sqrt(dX * dX + dZ * dZ);

            float yawAlvo = (float) (MathHelper.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F;
            float pitchAlvo = (float) -(MathHelper.atan2(dY, distHorizontal) * 180.0D / Math.PI);
            float velocidadeGiro = 15.0F;

            float diferencaYaw = MathHelper.wrapDegrees(yawAlvo - this.rotationYaw);
            float diferencaPitch = MathHelper.wrapDegrees(pitchAlvo - this.rotationPitch);

            if (diferencaYaw > velocidadeGiro) diferencaYaw = velocidadeGiro;
            if (diferencaYaw < -velocidadeGiro) diferencaYaw = -velocidadeGiro;
            if (diferencaPitch > velocidadeGiro) diferencaPitch = velocidadeGiro;
            if (diferencaPitch < -velocidadeGiro) diferencaPitch = -velocidadeGiro;

            this.rotationYaw += diferencaYaw;
            this.rotationPitch += diferencaPitch;
            this.rotationYawHead = this.rotationYaw;
        }

        public void olharParaCoordenadas(BlockPos coordenadas) {
            mirarSuave(coordenadas.getX(), coordenadas.getY(), coordenadas.getZ());
        }

        public void olharPara(Entity alvo) {
            mirarSuave(alvo.posX, alvo.posY + alvo.getEyeHeight(), alvo.posZ);
        }

        public void olharParaBloco(BlockPos pos) {
            if (pos == null) return;
            double dX = pos.getX() + 0.5 - this.posX;
            double dY = pos.getY() + 0.5 - (this.posY + this.getEyeHeight());
            double dZ = pos.getZ() + 0.5 - this.posZ;
            double distHorizontal = MathHelper.sqrt(dX * dX + dZ * dZ);
            this.rotationYaw = (float) (MathHelper.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F;
            this.rotationPitch = (float) -(MathHelper.atan2(dY, distHorizontal) * 180.0D / Math.PI);
            this.rotationYawHead = this.rotationYaw;
        }

        public void resetarOlhar() {
            this.rotationYawHead = this.rotationYaw;
            this.rotationPitch = 0.0F;
        }

        public void equiparMelhorEspada() {
            int melhorSlot = -1;
            float melhorDano = -1.0F;

            for (int i = 0; i < this.inventory.mainInventory.size(); i++) {
                ItemStack slot = this.inventory.mainInventory.get(i);
                if (!slot.isEmpty() && slot.getItem() instanceof ItemSword) {
                    ItemSword espadaAtual = (ItemSword) slot.getItem();
                    float danoAtual = espadaAtual.getAttackDamage();

                    if (danoAtual > melhorDano) {
                        melhorDano = danoAtual;
                        melhorSlot = i;
                    }
                }
            }

            if (melhorSlot == -1) {
                return;
            }

            if (melhorSlot < 9) {
                this.inventory.currentItem = melhorSlot;
            } else {
                ItemStack slotAtual = this.inventory.mainInventory.get(this.inventory.currentItem);
                ItemStack slotEspada = this.inventory.mainInventory.get(melhorSlot);

                this.inventory.mainInventory.set(this.inventory.currentItem, slotEspada);
                this.inventory.mainInventory.set(melhorSlot, slotAtual);
            }

            SPacketEntityEquipment pacoteEquip = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, this.getHeldItemMainhand());
            this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteEquip);
        }

        // ------------------------------------------
        // MECÂNICAS DE ATAQUE E SISTEMA DE COMBATE
        // ------------------------------------------

        /**
         * Controla o ataque mecânico do bot, desferindo golpes críticos baseados em pulos.
         */
        public void atacarAlvo() {
            if (alvoAtual != null) {
                equiparMelhorEspada();
                if (this.getDistanceSq(alvoAtual) <= 14.0D && !estadoAtual.equalsIgnoreCase("Towering")) {
                    if (this.ticksExisted % 10 == 0) {
                        this.resetCooldown();
                        this.swingArm(EnumHand.MAIN_HAND);

                        if (this.onGround) {
                            this.jump();
                        }

                        this.attackTargetEntityWithCurrentItem(alvoAtual);
                    }
                } else if (this.getDistanceSq(alvoAtual) <= 20.0D && estadoAtual.equalsIgnoreCase("Towering")) {
                    if (this.ticksExisted % 10 == 0) {
                        this.resetCooldown();
                        this.swingArm(EnumHand.MAIN_HAND);

                        if (this.onGround) {
                            this.jump();
                        }

                        this.attackTargetEntityWithCurrentItem(alvoAtual);
                    }
                } else if (this.getDistanceSq(alvoAtual) <= 14.0D && estadoAtual.equalsIgnoreCase("Defense")) {
                    if (this.ticksExisted % 5 == 0) {
                        this.resetCooldown();
                        this.swingArm(EnumHand.MAIN_HAND);

                        if (this.onGround) {
                            this.jump();
                        }

                        this.attackTargetEntityWithCurrentItem(alvoAtual);
                    }
                }
            }
        }

        public int encontrarSlotDeBloco() {
            for (int i = 0; i < this.inventory.mainInventory.size(); i++) {
                ItemStack slot = this.inventory.mainInventory.get(i);
                if (!slot.isEmpty() && slot.getItem() instanceof ItemBlock) {
                    Block bloco = ((ItemBlock) slot.getItem()).getBlock();
                    if (bloco.getDefaultState().getMaterial().isSolid() && bloco.isFullCube(bloco.getDefaultState()) && contarBlocos() >= 2) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public int contarBlocos() {
            int total = 0;
            for (ItemStack slot : this.inventory.mainInventory) {
                if (!slot.isEmpty() && slot.getItem() instanceof ItemBlock) {
                    Block bloco = ((ItemBlock) slot.getItem()).getBlock();
                    if (bloco.getDefaultState().getMaterial().isSolid() && bloco.isFullCube(bloco.getDefaultState())) {
                        total += slot.getCount();
                    }
                }
            }
            return total;
        }

        /**
         * Varre a área ao redor buscando entidades vivas numa bounding box expandida.
         */
        public List<Entity> radar(double raio, EntityLivingBase entidade) {
            WorldServer worldServer = (WorldServer) this.world;
            AxisAlignedBB caixa = entidade.getEntityBoundingBox().grow(raio, raio, raio);
            return worldServer.getEntitiesWithinAABB(Entity.class, caixa);
        }

        // ------------------------------------------
        // MECÂNICAS DE MINERAÇÃO E INTERAÇÃO COM MUNDO
        // ------------------------------------------

        /**
         * Faz buscas tridimensionais no mundo procurando por minérios expostos ao ar.
         */

        public EntityItem encontrarItemProximo(int raio) {
            EntityItem maisProximo = null;
            double menorDist = Double.MAX_VALUE;
            List<Entity> entidades = radar(raio, this);
            for (Entity e : entidades) {
                if (e instanceof EntityItem) {
                    double dist = this.getDistanceSq(e);
                    if (dist < menorDist) {
                        menorDist = dist;
                        maisProximo = (EntityItem) e;
                    }
                }
            }
            return maisProximo;
        }

        public int contarItem(Item material) {
            int total = 0;
            for (ItemStack slot : this.inventory.mainInventory) {
                if (!slot.isEmpty() && slot.getItem() == material) {
                    total += slot.getCount();
                }
            }
            return total;
        }

        public void adicionarItem(int quantidade, Item item) {
            ItemStack slot = new ItemStack(item, quantidade);
            this.inventory.addItemStackToInventory(slot);
            if (slot.getCount() > 0) {
                this.entityDropItem(slot, 0.0F);
            }
        }

        public int contarMobsProximos(int raio) {
            int total = 0;
            List<Entity> entities = radar(raio, this);

            for (Entity entity : entities) {
                if (entity instanceof EntityLivingBase && entity instanceof IMob) {
                    total++;
                }
            }
            return total;
        }

        public void consumirItem(int quantidade, Item item) {
            for (int i = 0; i < this.inventory.mainInventory.size(); i++) {
                ItemStack slot = this.inventory.mainInventory.get(i);
                if (!slot.isEmpty() && slot.getItem() == item) {
                    slot.shrink(quantidade);
                    if (slot.isEmpty()) {
                        this.inventory.mainInventory.set(i, ItemStack.EMPTY);
                    }
                }
            }
        }

        public boolean craftPicaretaMadeira() {
            if (contarItem(Items.STICK) >= 2 && contarItem(Item.getItemFromBlock(Blocks.PLANKS)) >= 3) {
                consumirItem(2, Items.STICK);
                consumirItem(3, Item.getItemFromBlock(Blocks.PLANKS));
                adicionarItem(1, Items.WOODEN_PICKAXE);
                return true;
            }
            return false;
        }

        public boolean craftSticks() {
            if (contarItem(Item.getItemFromBlock(Blocks.PLANKS)) >= 2) {
                consumirItem(2, Item.getItemFromBlock(Blocks.PLANKS));
                adicionarItem(4, Items.STICK);
                return true;
            }
            return false;
        }

        public boolean craftPicaretaPedra() {
            if (contarItem(Items.STICK) >= 2 && contarItem(Item.getItemFromBlock(Blocks.COBBLESTONE)) >= 3) {
                consumirItem(2, Items.STICK);
                consumirItem(3, Item.getItemFromBlock(Blocks.COBBLESTONE));
                adicionarItem(1, Items.STONE_PICKAXE);
                return true;
            }
            return false;
        }

        public boolean craftTable() {
            if (contarItem(Item.getItemFromBlock(Blocks.PLANKS)) >= 4) {
                consumirItem(4, Item.getItemFromBlock(Blocks.PLANKS));
                adicionarItem(1, Item.getItemFromBlock(Blocks.CRAFTING_TABLE));
                return true;
            }
            return false;
        }

        public boolean craftMadeiraRefinada() {
            if (contarItem(Item.getItemFromBlock(Blocks.LOG)) >= 1) {
                consumirItem(1, Item.getItemFromBlock(Blocks.LOG));
                adicionarItem(4, Item.getItemFromBlock(Blocks.PLANKS));
                return true;
            }
            return false;
        }

        public boolean temFerramenta(Item ferramenta) {
            for (ItemStack slot : this.inventory.mainInventory) {
                if (!slot.isEmpty() && slot.getItem() == ferramenta) {
                    return true;
                }
            }
            return false;
        }

        public BlockPos encontrarMinerioExposto(BlockPos centro, int raio) {
            BlockPos melhorPos = null;
            double menorDistanciaSq = Double.MAX_VALUE;

            for (int x = -raio; x <= raio; x++) {
                for (int y = -raio; y <= raio; y++) {
                    for (int z = -raio; z <= raio; z++) {
                        BlockPos posAtual = centro.add(x, y, z);
                        IBlockState blockState = this.world.getBlockState(posAtual);

                        if (MINERIOS.contains(blockState.getBlock()) && !blocosBlackList.contains(posAtual)) {
                            if (isBlocoExposto(posAtual)) {
                                double distSq = centro.distanceSq(posAtual);
                                if (distSq < menorDistanciaSq) {
                                    menorDistanciaSq = distSq;
                                    melhorPos = posAtual;
                                }
                            }
                        }
                    }
                }
            }
            return melhorPos;
        }

        public boolean isBlocoExposto(BlockPos bloco) {
            for (EnumFacing lado : EnumFacing.values()) {
                if (this.world.getBlockState(bloco.offset(lado)).getBlock() == Blocks.AIR) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Busca e equipa instantaneamente a melhor ferramenta no inventário para o bloco atual.
         */
        public void equiparMelhorFerramenta(IBlockState estado) {
            int melhorSlot = -1;
            float melhorVelocidade = 1.0F;

            for (int i = 0; i < this.inventory.mainInventory.size(); i++) {
                ItemStack slot = this.inventory.mainInventory.get(i);
                if (!slot.isEmpty()) {
                    boolean podeColetar = slot.canHarvestBlock(estado) || estado.getMaterial().isToolNotRequired();
                    float velocidade = slot.getDestroySpeed(estado);

                    if (podeColetar && velocidade > melhorVelocidade) {
                        melhorVelocidade = velocidade;
                        melhorSlot = i;
                    }
                }
            }

            if (melhorSlot != -1) {
                if (melhorSlot < 9) {
                    this.inventory.currentItem = melhorSlot;
                } else {
                    ItemStack slotatual = this.inventory.mainInventory.get(this.inventory.currentItem);
                    ItemStack melhorItem = this.inventory.mainInventory.get(melhorSlot);
                    this.inventory.mainInventory.set(this.inventory.currentItem, melhorItem);
                    this.inventory.mainInventory.set(melhorSlot, slotatual);
                }
                SPacketEntityEquipment pacoteEquip = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, this.getHeldItemMainhand());
                this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteEquip);
            }
        }

        /**
         * Troca ou aceita itens quando um jogador real clica com o botão direito no bot.
         */
        @Override
        public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
            if (hand == EnumHand.MAIN_HAND) {
                ItemStack playerItem = player.getHeldItemMainhand();
                ItemStack botItem = this.getHeldItemMainhand();

                if (!playerItem.isEmpty()) {
                    if (!botItem.isEmpty() && !player.addItemStackToInventory(botItem.copy())) {
                        this.entityDropItem(botItem, 0.0F);
                    }
                    this.setHeldItem(EnumHand.MAIN_HAND, playerItem.copy());
                    player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);

                    SPacketEntityEquipment pacoteEquip = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, this.getHeldItemMainhand());
                    this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteEquip);
                    return true;
                } else if (!botItem.isEmpty()) {
                    if (!player.addItemStackToInventory(botItem.copy())) {
                        player.setHeldItem(EnumHand.MAIN_HAND, botItem.copy());
                    }
                    this.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);

                    SPacketEntityEquipment pacoteEquip = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteEquip);
                    return true;
                }
            }
            return super.processInitialInteract(player, hand);
        }

        // ------------------------------------------
        // SOBREVIVÊNCIA, QUEDA, SOM E FÍSICA RETROATIVADOS
        // ------------------------------------------

        @Override
        public void fall(float distance, float multiplier) {
            if (this.capabilities.isCreativeMode || this.isSpectator() || this.capabilities.allowFlying) {
                this.getServer().sendMessage(new TextComponentString("Você está no criativo ou pode voar e não toma dano de queda!"));
                return;
            }

            super.fall(distance, multiplier);
            float distanciaAcumulada = distance - 3.0F;
            PotionEffect efeitopocao = this.getActivePotionEffect(MobEffects.JUMP_BOOST);

            if (efeitopocao != null) {
                distanciaAcumulada -= (float) efeitopocao.getAmplifier() + 1;
            }

            if (distanciaAcumulada > 0.0F) {
                int danoFinal = MathHelper.ceil(distanciaAcumulada * multiplier);
                if (danoFinal > 0) {
                    this.playSound(this.getFallSound(danoFinal), 1.0F, 1.0F);
                    this.attackEntityFrom(DamageSource.FALL, (float) danoFinal);
                }
            }
        }

        @Override
        public void knockBack(Entity entityIn, float strength, double xRatio, double zRatio) {
            this.isAirBorne = true;
            float f = MathHelper.sqrt(xRatio * xRatio + zRatio * zRatio);

            this.motionX -= xRatio / (double) f * (double) strength;
            this.motionZ -= zRatio / (double) f * (double) strength;
            this.motionY /= 2.0D;
            this.motionY += (double) strength;

            if (this.motionY > 0.4000000059604645D) {
                this.motionY = 0.4000000059604645D;
            }
            this.velocityChanged = false;
        }

        public void tocarSom(SoundEvent som, SoundCategory categoria) {
            this.world.playSound(null, this.posX, this.posY, this.posZ, som, categoria, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
        }

        // ------------------------------------------
        // TICK LOOP COMPORTAMENTAL DA ENTIDADE (UPDATE)
        // ------------------------------------------


        @Override
        public void onUpdate() {
            if (!carregouMemoria) {
                cerebro.carregarMemoria();
                this.getServer().sendMessage(new TextComponentString("Memoria CARREGADA!"));
                carregouMemoria = true;
            }

            if (this.collidedHorizontally && this.onGround && (Math.abs(this.motionX) > 0.001D || Math.abs(this.motionZ) > 0.001D)) {
                this.jump();
                if (this.motionX > this.motionZ) {
                    if (this.motionX < 0) {
                        this.motionX -= 0.1D;
                    } else {
                        this.motionX += 0.1D;
                    }
                } else {
                    if (this.motionZ < 0) {
                        this.motionZ -= 0.1D;
                    } else {
                        this.motionZ += 0.1D;
                    }
                }
            }

            // Módulo de Respawn/Morte do FakePlayer
            if (this.getHealth() <= 0 || this.isDead) {
                if (!renascendo) {
                    if (tickRenascer != 0) {
                        tickRenascer--;
                        return;
                    }
                    cerebro.salvarMemoria();
                    this.getServer().sendMessage(new TextComponentString("Memoria SALVA!"));
                    renascendo = true;

                    WorldServer mundo = (WorldServer) this.world;
                    MinecraftServer servidor = this.getServer();
                    String nome = this.getName();

                    this.setDead();
                    SPacketPlayerListItem pacoteRemove = new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, this);
                    servidor.getPlayerList().sendPacketToAllPlayers(pacoteRemove);
                    servidor.getPlayerList().getPlayers().remove(this);
                    Bots.bots.remove(Bots.PegarBot(this.getName()));

                    servidor.addScheduledTask(() -> ComandoFakePlayer.criarbot(servidor, mundo, nome, null));
                    return;
                }
            }

            if (!this.onGround) {
                this.motionY -= 0.08D;
            }

            // Limpeza de alvos inválidos ou mortos
            if (alvoAtual != null) {
                if (!(alvoAtual.isEntityAlive()) || alvoAtual.getHealth() <= 0 || this.getDistanceSq(alvoAtual) > 400.0D || (alvoAtual instanceof EntityPlayerMP && ((EntityPlayerMP) alvoAtual).isSpectator())) {
                    alvoAtual = null;
                }
            }

            if (cooldownRadar > 0) {
                cooldownRadar--;
            }

            // --- MAQUINA DE ESTADOS DO BOT ---

            // ESTADO: ATTACK
            if (estadoAtual.equalsIgnoreCase("Attack")) {
                WorldServer worldServer = (WorldServer) this.world;
                AxisAlignedBB caixa = getEntityBoundingBox().grow(16, 16, 16);
                List<EntityLivingBase> entidades = worldServer.getEntitiesWithinAABB(EntityLivingBase.class, caixa);

                double distMenor = 36.0D;
                EntityLivingBase melhorAlvo = null;

                for (EntityLivingBase entidade : entidades) {
                    if (entidade == this || entidade.isDead) continue;
                    if (entidade instanceof EntityPlayerMP && ((EntityPlayerMP) entidade).isSpectator()) continue;

                    double dist = this.getDistanceSq(entidade);
                    if (melhorAlvo == null && entidade instanceof IMob || entidade instanceof IMob && dist <= distMenor) {
                        melhorAlvo = entidade;
                        distMenor = dist;
                    }
                }
                alvoAtual = melhorAlvo;

                if (alvoAtual != null) {
                    double distSq = this.getDistanceSq(alvoAtual);
                    if (distSq <= 49.0D && this.canEntityBeSeen(alvoAtual)) {
                        this.olharPara(alvoAtual);
                    } else {
                        resetarOlhar();
                    }

                    if (distSq > 9.0) {
                        if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            gerarCaminhoAteAlvo(null);
                        }
                        andarPath(null);
                    }
                    if (this.getDistanceSq(alvoAtual) <= 9.0) {
                        this.atacarAlvo();
                    }
                } else {
                    resetarOlhar();
                }
            }


            if (estadoAtual.equalsIgnoreCase("TakeStone")) {
                if (blocoSendoMinerado == null) {
                    blocoSendoMinerado = encontrarPedra(15);
                } else {
                    double distSq = this.getDistanceSq(blocoSendoMinerado);
                    if (distSq <= 12.0D) {
                        olharParaBloco(blocoSendoMinerado);
                        equiparMelhorFerramenta(this.world.getBlockState(blocoSendoMinerado));
                        quebrarBloco(blocoSendoMinerado);
                    } else {
                        if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            gerarCaminhoAteAlvo(blocoSendoMinerado);
                        }
                        andarPath(blocoSendoMinerado);
                        olharParaBloco(blocoSendoMinerado);
                    }
                }
            }

            // ESTADO: EXPLORING
            if (estadoAtual.equalsIgnoreCase("Exploring")) {
                if (this.ticksExisted % 100 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                    double angulo = Math.random() * 2 * Math.PI;
                    int raio = 10 + (int) (Math.random() * 15);
                    int alvoX = (int)(this.posX + Math.cos(angulo) * raio);
                    int alvoZ = (int)(this.posZ + Math.sin(angulo) * raio);
                    BlockPos posAlvo = new BlockPos(alvoX, this.posY, alvoZ);

                    gerarCaminhoAteAlvo(posAlvo);
                }
                if (!caminhoAtual.isEmpty()) {
                    BlockPos destino = caminhoAtual.get(caminhoAtual.size() - 1);
                    andarPath(destino);
                    olharParaCoordenadas(destino);
                } else {
                    resetarOlhar();
                }
            }

            // ESTADO: STAND
            if (estadoAtual.equalsIgnoreCase("Stand")) {
                if (!parar) {
                    parar = true;
                    resetarOlhar();
                    this.motionX = 0;
                    this.motionZ = 0;
                }
            } else {
                parar = false;
            }

            if (estadoAtual.equalsIgnoreCase("CraftTools")) {
                if (!temFerramenta(Item.getItemFromBlock(Blocks.CRAFTING_TABLE))) { // não tem mesa de trabalho
                    if (craftTable()) { // crafta mesa de trabalho
                        this.getServer().sendMessage(new TextComponentString("§aCrafting table criada!"));
                    }
                } else if (!temFerramenta(Items.WOODEN_PICKAXE)) { // não tem picareta de madeira
                    if (!temFerramenta(Items.STONE_PICKAXE) && !temFerramenta(Items.IRON_PICKAXE) && !temFerramenta(Items.DIAMOND_PICKAXE)) {
                        // não tem nenhuma picareta
                        if (craftPicaretaMadeira()) { // crafta picareta
                            this.getServer().sendMessage(new TextComponentString("§aPicareta de madeira criada!"));
                        } else {
                            if (contarItem(Items.STICK) < 2) { // não tem stick
                                if (craftSticks()) { // crafta sticks
                                    this.getServer().sendMessage(new TextComponentString("§aSticks criados!"));
                                } else { // não tem planks
                                    if (craftMadeiraRefinada()) { // crafta planks
                                        this.getServer().sendMessage(new TextComponentString("§aPlanks criados!"));
                                    }
                                }
                            } else if (contarItem(Item.getItemFromBlock(Blocks.PLANKS)) < 3) { // não tem planks
                                if (craftMadeiraRefinada()) { // crafta planks
                                    this.getServer().sendMessage(new TextComponentString("§aPlanks criados!"));
                                }
                            }
                        }
                    }
                } else if (!temFerramenta(Items.STONE_PICKAXE)) { // não tem picareta de pedra
                    if (!temFerramenta(Items.IRON_PICKAXE) && !temFerramenta(Items.DIAMOND_PICKAXE)) {
                        // não tem nenhuma picareta
                        if (craftPicaretaPedra()) { // crafta picareta de pedra
                            this.getServer().sendMessage(new TextComponentString("§aPicareta de pedra criada!"));
                        } else {
                            if (contarItem(Items.STICK) < 2) { // não tem stick
                                if (craftSticks()) { // crafta sticks
                                    this.getServer().sendMessage(new TextComponentString("§aSticks criados!"));
                                } else { // não tem planks
                                    if (craftMadeiraRefinada()) { // craft planks
                                        this.getServer().sendMessage(new TextComponentString("§aPlanks criados!"));
                                    }
                                }
                            }
                        }
                    }
                } else {
                    this.getServer().sendMessage(new TextComponentString("§2CraftTools concluído!"));
                    return;
                }
            }

            // ESTADO: EAT
            if (estadoAtual.equalsIgnoreCase("Eat")) {
                NonNullList<ItemStack> inventory = this.inventory.mainInventory;
                int foodSlot = -1;

                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack slot = inventory.get(i);
                    if (!slot.isEmpty() && slot.getItem() instanceof ItemFood) {
                        foodSlot = i;
                        break;
                    }
                }

                if (foodSlot == -1) {
                    if (this.ticksExisted % 10 == 0) {
                        double distMenor = 256.0D;
                        EntityLivingBase melhorAlvo = null;
                        EntityItem melhorItem = null;

                        List<Entity> entidadesProximas = radar(16, this);
                        List<EntityAnimal> animaisProximos = new ArrayList<>();
                        List<EntityItem> itensProximos = new ArrayList<>();

                        for (Entity entidade : entidadesProximas) {
                            if (entidade instanceof EntityAnimal) {
                                animaisProximos.add((EntityAnimal) entidade);
                            } else if (entidade instanceof EntityItem && ((EntityItem) entidade).getItem().getItem() instanceof ItemFood) {
                                itensProximos.add((EntityItem) entidade);
                            }
                        }

                        if (!animaisProximos.isEmpty()) {
                            for (EntityAnimal animal : animaisProximos) {
                                double dist = this.getDistanceSq(animal);

                                if (dist < distMenor) {
                                    distMenor = dist;
                                    melhorAlvo = animal;
                                }
                            }
                        }

                        if (!itensProximos.isEmpty()) {
                            for (EntityItem item : itensProximos) {
                                double dist = this.getDistanceSq(item);

                                if (dist < distMenor) {
                                    distMenor = dist;
                                    melhorItem = item;
                                }
                            }
                        }

                        alvoItem = melhorItem;
                        alvoAnimal = melhorAlvo;
                    }
                    if (alvoItem != null) {
                        if (caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            gerarCaminhoAteAlvo(alvoItem.getPosition());
                        }
                        andarPath(alvoItem.getPosition());
                    } else {
                        if (alvoAnimal != null) {
                            alvoAtual = alvoAnimal;
                            if (caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                                gerarCaminhoAteAlvo(null);
                            }
                            andarPath(null);
                            if (this.canEntityBeSeen(alvoAtual)) {
                                olharPara(alvoAtual);
                            } else {
                                resetarOlhar();
                            }
                            atacarAlvo();
                        } else {
                            alvoAnimal = null;
                            alvoItem = null;
                        }
                    }
                }

                if (foodSlot != -1) {
                    if (foodSlot < 9) {
                        this.inventory.currentItem = foodSlot;
                    } else {
                        int hotbarAtual = this.inventory.currentItem;
                        ItemStack itemSegurado = inventory.get(hotbarAtual);
                        ItemStack comidaEncontrada = inventory.get(foodSlot);

                        inventory.set(hotbarAtual, comidaEncontrada);
                        inventory.set(foodSlot, itemSegurado);
                    }
                }
                SPacketEntityEquipment pacoteSegurar = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, this.getHeldItemMainhand());
                this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteSegurar);
                if (this.getFoodStats().getFoodLevel() < 20) {

                ItemStack maoPrincipal = this.getHeldItemMainhand();
                if (maoPrincipal.getItem() instanceof ItemFood) {
                    if (!this.isHandActive()) {
                        this.setActiveHand(EnumHand.MAIN_HAND);
                        tickComer = 32;
                    }

                    tickComer--;
                    if (tickComer % 4 == 0) {
                        tocarSom(SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS);
                    }

                    if (tickComer <= 0) {
                        ItemFood comida = (ItemFood) maoPrincipal.getItem();
                        this.getFoodStats().addStats(comida, maoPrincipal);
                        tocarSom(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS);

                        maoPrincipal.shrink(1);
                        this.resetActiveHand();

                        if (maoPrincipal.isEmpty()) {
                            SPacketEntityEquipment pacoteEquip = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                            this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteEquip);
                            }
                        }
                    }
                }
            }

            // ESTADO: DEFENSE
            if (estadoAtual.equalsIgnoreCase("Defense")) {
                    WorldServer worldServer = (WorldServer) this.world;
                    AxisAlignedBB caixa = getEntityBoundingBox().grow(16, 16, 16);
                    List<EntityLivingBase> entidades = worldServer.getEntitiesWithinAABB(EntityLivingBase.class, caixa);

                    double distMenor = 256.0D;
                    EntityLivingBase melhorAlvo = null;

                    for (EntityLivingBase entidade : entidades) {
                        if (entidade == this || entidade.isDead) continue;
                        if (entidade instanceof IMob) {
                            double dist = this.getDistanceSq(entidade);
                            if (dist < distMenor) {
                                distMenor = dist;
                                melhorAlvo = entidade;
                            }
                        }
                    }
                    alvoAtual = melhorAlvo;


                if (alvoAtual != null) {
                    double distSq = this.getDistanceSq(alvoAtual);
                    if (distSq <= 14.0D && this.canEntityBeSeen(alvoAtual)) {
                        this.olharPara(alvoAtual);
                        atacarAlvo();
                    }

                    if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                        BlockPos posFuga = calcularFuga(alvoAtual, 20);
                        gerarCaminhoAteAlvo(posFuga);
                    }

                    if (!caminhoAtual.isEmpty()) {
                        BlockPos destinoFuga = caminhoAtual.get(caminhoAtual.size() - 1);
                        andarPath(destinoFuga);
                    }
                } else {
                    resetarOlhar();
                }

                if (this.ticksExisted % 20 == 0) {
                    if (this.getFoodStats().getFoodLevel() >= 11) {
                        this.heal(1.0F);
                        this.getFoodStats().addExhaustion(3.0F);
                    }
                }
            }

            if (estadoAtual.equalsIgnoreCase("TakeWood")) {
                if (blocoSendoMinerado == null) {
                    resetarOlhar();
                    blocoSendoMinerado = encontrarArvore(25);
                    if (blocoSendoMinerado != null) {
                        IBlockState estado = this.world.getBlockState(blocoSendoMinerado);
                        if (estado.getBlock() instanceof BlockAir) {
                            blocoSendoMinerado = null;
                            progressoMineracao = 0.0F;
                            caminhoAtual.clear();
                            indexCaminho = 0;
                        }
                        gerarCaminhoAteAlvo(blocoSendoMinerado);
                        andarPath(blocoSendoMinerado);
                        tickQuebrarMadeira = 80;
                    }
                } else {
                    if (blocoSendoMinerado != null && (blocoSendoMinerado.getY() - this.getPosition().getY()) >= 4 && this.onGround) {
                        this.jump();
                    }
                    IBlockState estado = this.world.getBlockState(blocoSendoMinerado);
                    if (estado.getBlock() instanceof BlockAir) {
                        blocoSendoMinerado = null;
                        progressoMineracao = 0.0F;
                        caminhoAtual.clear();
                        indexCaminho = 0;
                        return;
                    }

                    double distSq = this.getDistanceSq(blocoSendoMinerado);

                    if (distSq <= 20.0D) {
                        boolean temFolhaAFrente = false;
                        BlockPos folha = null;
                        for (EnumFacing face : EnumFacing.HORIZONTALS) {
                            BlockPos pos = blocoSendoMinerado.offset(face);
                            if (this.world.getBlockState(pos).getBlock() instanceof BlockLeaves) {
                                double distBot = this.getDistanceSq(pos);
                                double distAlvo = this.getDistanceSq(blocoSendoMinerado);
                                if (distBot < distAlvo) {
                                    temFolhaAFrente = true;
                                    folha = pos;
                                    break;
                                }
                            }
                        }

                        if (temFolhaAFrente) {
                            olharParaBloco(folha);
                            quebrarBloco(folha);
                        } else {
                            olharParaBloco(blocoSendoMinerado);
                            equiparMelhorFerramenta(this.world.getBlockState(blocoSendoMinerado));
                            quebrarBloco(blocoSendoMinerado);
                        }

                    } else {
                        this.getServer().sendMessage(new TextComponentString("Else chamado"));
                        if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            this.getServer().sendMessage(new TextComponentString("gerou caminho"));
                            gerarCaminhoAteAlvo(blocoSendoMinerado);
                        }
                        andarPath(blocoSendoMinerado);
                        this.getServer().sendMessage(new TextComponentString("andar path chamado"));
                        if (tickQuebrarMadeira > 0) {
                            tickQuebrarMadeira--;
                        } else {
                            tickQuebrarMadeira = 80;
                            if (this.motionX < 0.1D && this.motionZ < 0.1D) {
                                blocosBlackList.add(blocoSendoMinerado);
                                this.getServer().sendMessage(new TextComponentString("Bloco: " + this.world.getBlockState(blocoSendoMinerado).getBlock().getLocalizedName() + " adicionado à blacklist!"));
                                blocoSendoMinerado = null;
                                progressoMineracao = 0.0F;
                                caminhoAtual.clear();
                                indexCaminho = 0;
                                return;
                            }
                        }
                        olharParaBloco(blocoSendoMinerado);
                    }
                }
            }

            // ESTADO: MINNING
            if (estadoAtual.equalsIgnoreCase("Minning")) {
                BlockPos meuPos = this.getPosition();

                if (blocoSendoMinerado != null) {
                    IBlockState estadoAtualBloco = this.world.getBlockState(blocoSendoMinerado);
                    if (!MINERIOS.contains(estadoAtualBloco.getBlock())) {
                        this.world.sendBlockBreakProgress(this.getEntityId(), blocoSendoMinerado, -1);
                        blocoSendoMinerado = null;
                        progressoMineracao = 0.0F;
                    }
                }


                if (blocoSendoMinerado != null) {
                    olharParaBloco(blocoSendoMinerado);

                    if (meuPos.distanceSq(blocoSendoMinerado) <= 16.0D) {
                        this.motionX *= 0.91D;
                        this.motionY *= 0.98D;
                        this.motionZ *= 0.91D;

                        IBlockState state = this.world.getBlockState(blocoSendoMinerado);
                        equiparMelhorFerramenta(state);

                        boolean podeColher = this.getHeldItemMainhand().canHarvestBlock(state) || state.getMaterial().isToolNotRequired();
                        if (!podeColher) {
                            this.world.sendBlockBreakProgress(this.getEntityId(), blocoSendoMinerado, -1);
                            blocoSendoMinerado = null;
                            return;
                        }

                        if (this.ticksExisted % 4 == 0) {
                            this.swingArm(EnumHand.MAIN_HAND);
                        }

                        float danoPorTick = state.getPlayerRelativeBlockHardness(this, this.world, blocoSendoMinerado);
                        progressoMineracao += danoPorTick;

                        int estagioRachadura = (int) (progressoMineracao * 10.0F);
                        this.world.sendBlockBreakProgress(this.getEntityId(), blocoSendoMinerado, estagioRachadura);

                        if (progressoMineracao >= 1.0F) {
                            this.interactionManager.tryHarvestBlock(blocoSendoMinerado);
                            this.world.sendBlockBreakProgress(this.getEntityId(), blocoSendoMinerado, -1);
                            blocoSendoMinerado = null;
                            progressoMineracao = 0.0F;
                        }
                    } else {
                        if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            gerarCaminhoAteAlvo(blocoSendoMinerado);
                        }
                        andarPath(blocoSendoMinerado);
                    }
                }
            }

            if (estadoAtual.equalsIgnoreCase("TakeItens")) {
                if (alvoItemChao == null || !alvoItemChao.isEntityAlive()) {
                    alvoItemChao = encontrarItemProximo(15);
                    if (alvoItemChao != null) {
                        if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            gerarCaminhoAteAlvo(alvoItemChao.getPosition());
                        }
                    }
                } else {
                    double distSq = this.getDistanceSq(alvoItemChao);
                    if (distSq <= 2.0) {
                        this.motionX = 0;
                        this.motionZ = 0;
                    } else {
                        if (this.ticksExisted % 20 == 0 || caminhoAtual.isEmpty() || indexCaminho >= caminhoAtual.size()) {
                            gerarCaminhoAteAlvo(alvoItemChao.getPosition());
                        }
                        andarPath(alvoItemChao.getPosition());
                        olharPara(alvoItemChao);
                    }
                }
            }

            if (estadoAtual.equalsIgnoreCase("Towering")) {
                List<Entity> entidadesNoLivingBase = radar(16, this);
                List<EntityLivingBase> entidadesLivingBase = new ArrayList<>();

                if (entidadesNoLivingBase == null) {
                    entidadesNoLivingBase = new ArrayList<>();
                }

                for (Entity noLivingBase : entidadesNoLivingBase) {
                    if (noLivingBase instanceof EntityLivingBase) {
                        entidadesLivingBase.add((EntityLivingBase) noLivingBase);
                    }
                }

                EntityLivingBase mobMaisProximo = null;
                double distMin = Double.MAX_VALUE;
                int mobsPerto = 0;

                for (EntityLivingBase livingBase : entidadesLivingBase) {
                    if (livingBase instanceof IMob && !livingBase.isDead) {
                        mobsPerto++;
                        double dist = this.getDistanceSq(livingBase);
                        if (dist < distMin) {
                            mobMaisProximo = livingBase;
                            distMin = dist;
                        }
                    }
                }

                if (mobsPerto > 0 && mobMaisProximo != null) {


                    if (distMin < 16.0D && blocosDaTorre.size() < 3) {
                        double dx = this.posX - mobMaisProximo.posX;
                        double dz = this.posZ - mobMaisProximo.posZ;
                        double dist = MathHelper.sqrt(dx * dx + dz * dz);
                        if (dist > 0) {
                            this.motionX = (dx / dist) * 0.35;
                            this.motionZ = (dz / dist) * 0.35;
                        }
                        this.olharPara(mobMaisProximo);
                    } else if (blocosDaTorre.size() < 3) {
                        this.motionX = 0;
                        this.motionZ = 0;

                        this.rotationPitch = 90.0F;
                        this.rotationYawHead = this.rotationYaw;

                        int slotBloco = encontrarSlotDeBloco();
                        if (slotBloco != -1) {
                            if (slotBloco < 9) {
                                this.inventory.currentItem = slotBloco;
                            } else {
                                ItemStack stackBloco = this.inventory.mainInventory.get(slotBloco);
                                ItemStack stackMao = this.inventory.mainInventory.get(this.inventory.currentItem);
                                this.inventory.mainInventory.set(this.inventory.currentItem, stackBloco);
                                this.inventory.mainInventory.set(slotBloco, stackMao);
                            }
                            SPacketEntityEquipment pacoteEquip = new SPacketEntityEquipment(this.getEntityId(), EntityEquipmentSlot.MAINHAND, this.getHeldItemMainhand());
                            this.getServer().getPlayerList().sendPacketToAllPlayers(pacoteEquip);

                            this.motionX = 0;
                            this.motionZ = 0;
                            if (this.onGround) {
                                if (!blocosDaTorre.isEmpty()) {
                                    BlockPos blocoBase = blocosDaTorre.get(0);
                                    BlockPos posAtual = new BlockPos(this.posX, this.posY, this.posZ);
                                    if (posAtual.getX() != blocoBase.getX() || posAtual.getZ() != blocoBase.getZ()) {
                                        blocosDaTorre.clear();
                                    }
                                }
                                this.jump();
                            } else {
                                BlockPos posBaixo = new BlockPos(this.posX, this.posY - 0.5D, this.posZ);
                                BlockPos posBaixo2 = new BlockPos(this.posX, MathHelper.floor(this.posY) - 1.0D, this.posZ);
                                IBlockState blocoBaixo = this.world.getBlockState(posBaixo);
                                IBlockState blocoBaixo2 = this.world.getBlockState(posBaixo2);

                                if ((blocoBaixo.getBlock() == Blocks.AIR || blocoBaixo.getMaterial().isReplaceable()) && blocoBaixo2.getBlock() != Blocks.AIR) {
                                    ItemStack blocoSlot = this.getHeldItemMainhand();

                                    if (!blocoSlot.isEmpty() && blocoSlot.getItem() instanceof ItemBlock) {
                                        Block blocoModel = ((ItemBlock) blocoSlot.getItem()).getBlock();

                                        this.world.setBlockState(posBaixo, blocoModel.getDefaultState());
                                        blocoSlot.shrink(1);
                                        this.world.playSound(null, posBaixo, blocoModel.getSoundType().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);

                                        blocosDaTorre.add(posBaixo);

                                        this.setPosition(this.posX, posBaixo.getY() + 1.0D, this.posZ);
                                        this.motionY = 0;
                                        this.onGround = true;
                                    }
                                }
                            }
                        }
                    } else {
                        this.motionX *= 0.91D;
                        this.motionY *= 0.98D;
                        this.motionZ *= 0.91D;
                        this.setSneaking(true);

                        alvoAtual = mobMaisProximo;
                        this.olharPara(alvoAtual);
                        atacarAlvo();
                    }
                } else {
                    this.setSneaking(false);

                    if (!blocosDaTorre.isEmpty()) {
                        BlockPos topoTorre = blocosDaTorre.get(blocosDaTorre.size() - 1);
                        IBlockState estadoTopo = this.world.getBlockState(topoTorre);

                        if (estadoTopo.getBlock() != Blocks.AIR) {
                            this.olharParaBloco(topoTorre);
                            this.equiparMelhorFerramenta(estadoTopo);

                            if (this.ticksExisted % 4 == 0) {
                                this.swingArm(EnumHand.MAIN_HAND);
                            }

                            float danoPorTick = estadoTopo.getPlayerRelativeBlockHardness(this, this.world, topoTorre);
                            progressoMineracao += danoPorTick;

                            int estagioRachadura = (int) (progressoMineracao * 10.0F);
                            this.world.sendBlockBreakProgress(this.getEntityId(), topoTorre, estagioRachadura);

                            if (progressoMineracao >= 1.0F) {
                                this.interactionManager.tryHarvestBlock(topoTorre);
                                this.world.sendBlockBreakProgress(this.getEntityId(), topoTorre, -1);
                                blocosDaTorre.remove(blocosDaTorre.size() -1);
                                progressoMineracao = 0.0F;
                            }
                        } else {
                            blocosDaTorre.remove(blocosDaTorre.size() -1);
                        }
                    } else {
                        blocosDaTorre.clear();
                    }
                }
            }


            // Gerenciamento Periódico de Fome e Atualizações de Sync (1 Vez por Segundo)
            if (this.ticksExisted % 20 == 0) {
                if (this.getFoodStats().getFoodLevel() >= 11) {
                    this.heal(1.0F);
                    this.getFoodStats().addExhaustion(3.0F);
                }

                if (estadoAtual.equalsIgnoreCase("Attack")) {
                    this.addExhaustion(0.35F);
                } else if (this.motionX * this.motionX + this.motionZ * this.motionZ > 0.001D) {
                    this.addExhaustion(0.1F);
                } else {
                    this.addExhaustion(0.005F);
                }

                this.getFoodStats().onUpdate(this);
                estadoAtual = Bots.PegarBot(this.getName()).estadoAtual;

                Object[] inputsAgora = {
                        this.getHealth(),
                        contarMobsProximos(16),
                        contarItem(Item.getItemFromBlock(Blocks.LOG)),
                        this.getFoodStats().getFoodLevel(),
                        contarItem(Item.getItemFromBlock(Blocks.COBBLESTONE)) + contarItem(Item.getItemFromBlock(Blocks.STONE)),
                };

                 this.getServer().sendMessage(new TextComponentString(cerebro.printStatus(inputsAgora)));

                 double x = this.posX;
                 double y = this.posY;
                 double z = this.posZ;

                AIBot.sendPacketToAllPlayers(new GuiStatePacket(x, y, z, 0, true));

            }

            // Processamento do motor físico de atrito nativo do MC
            boolean estavaNoAr = !this.onGround;
            super.onUpdate();

            this.motionX *= 0.91D;
            this.motionY *= 0.98D;
            this.motionZ *= 0.91D;

            if (this.onGround) {
                this.motionX *= 0.6D;
                this.motionZ *= 0.6D;
            }

            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);

            if (this.onGround && estavaNoAr) {
                if (this.fallDistance > 0.0F) {
                    this.fall(this.fallDistance, 1.0F);
                    this.fallDistance = 0.0F;
                }
            }

            if (this.ticksExisted % 10 == 0) {
                AxisAlignedBB pegarItemBox = this.getEntityBoundingBox().grow(1, 0.5, 1);
                List<EntityItem> itens = this.getServerWorld().getEntitiesWithinAABB(EntityItem.class, pegarItemBox);

                for (EntityItem item : itens) {
                    ItemStack slotItem = item.getItem();
                    this.inventory.addItemStackToInventory(slotItem);
                    item.setDead();
                    tocarSom(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS);
                }
            }

            // ============================
            // IA PRINCIPAL (REDE NEURAL)
            // ============================


            if (cooldownDecisaoTick > 0) {
                cooldownDecisaoTick--;
            } else {
                cooldownDecisaoTick = 10;

                if (this.isHandActive() && this.getActiveItemStack().getItem() instanceof ItemFood) {
                    return;
                }
                if (blocoSendoMinerado != null && progressoMineracao > 0) {
                    return;
                }
                if (blocoSendoMinerado != null && this.world.getBlockState(blocoSendoMinerado) instanceof BlockAir) {
                    blocoSendoMinerado = null;
                }


                double vidaAgora = this.getHealth();
                int mobsAgora = contarMobsProximos(16);
                int madeirasAgora = contarItem(Item.getItemFromBlock(Blocks.LOG));
                double fomeAgora = this.getFoodStats().getFoodLevel();
                int pedrasAgora = contarItem(Item.getItemFromBlock(Blocks.COBBLESTONE)) + contarItem(Item.getItemFromBlock(Blocks.STONE));
                boolean estaParado = Math.abs(this.motionX) < 0.001D && Math.abs(this.motionZ) < 0.001D;
                boolean estaComendo = this.isHandActive() && this.getActiveItemStack().getItem() instanceof ItemFood;
                boolean estaQuebrando = blocoSendoMinerado != null && progressoMineracao > 0;
                boolean temArmadura = false;
                int nivelArmaduraAgora = 0;
                for (ItemArmor armadura : ARMADURAS) if (this.inventory.mainInventory.contains(armadura)) { temArmadura = true; } else temArmadura = false;
                for (int i = 0; i < 4; i++) if (this.inventory.armorItemInSlot(i) != null && this.inventory.armorItemInSlot(i).getItem() instanceof ItemArmor) { nivelArmaduraAgora += ((ItemArmor) (this.inventory.armorItemInSlot(i).getItem())).damageReduceAmount; }

                Object[] inputsAgora = {
                        vidaAgora,
                        mobsAgora,
                        madeirasAgora,
                        fomeAgora,
                        pedrasAgora,
                        temArmadura,
                        nivelArmaduraAgora
                };

                Object[] inputsAntes = {
                        vidaAntes,
                        mobsAntes,
                        madeirasAntes,
                        fomeAntes,
                        pedrasAntes,
                        temArmaduraAntes,
                        nivelArmaduraAntes
                };

                // 1. Perdeu vida
                if (vidaAgora < vidaAntes) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, -1.0);
                }
                // 2. Matou mob
                if (mobsAgora < mobsAntes) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, 1.0);
                }
                // 3. Coletou madeira (se ação foi TakeWood)
                if (madeirasAgora > madeirasAntes && ultimaAcao == 2) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, 1.0);
                }

                if (tickQuebrarMadeira > 0) {
                    tickQuebrarMadeira--;
                } else {
                    tickQuebrarMadeira = 80;
                    if (madeirasAgora <= madeirasAntes && blocoSendoMinerado == null && ultimaAcao == 2 && estaParado) {
                        cerebro.recompensar(inputsAntes, ultimaAcao, -1.5);
                    }
                    if (pedrasAgora <= pedrasAntes && blocoSendoMinerado == null && ultimaAcao == 4 && estaParado) {
                        cerebro.recompensar(inputsAntes, ultimaAcao, -1.5);
                    }
                }

                // 5. Fome baixa e não comeu/não fugiu
                if (fomeAgora < 10 && ultimaAcao != 3 && ultimaAcao != 1 && mobsAntes <= 0) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, -1.0);
                }
                // 6. Defesa desnecessária
                if (ultimaAcao == 1 && mobsAntes <= 0 && vidaAntes >= 12) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, -1.5);
                }
                // 7. Comeu com fome cheia
                if (fomeAgora > 18 && ultimaAcao == 3) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, -2.0);
                }
                // 8. Atacou sem mobs
                if (mobsAgora <= 0 && ultimaAcao == 0) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, -2.0);
                }
                // 9. Fome aumentou (comeu)
                if (fomeAgora > fomeAntes) {
                    cerebro.recompensar(inputsAntes, ultimaAcao, 1.4);
                }

                int acao = cerebro.decidirAcao(inputsAgora);

                switch (acao) {
                    case 0: estadoAtual = "Attack"; break;
                    case 1: estadoAtual = "Defense"; break;
                    case 2: estadoAtual = "TakeWood"; break;
                    case 3: estadoAtual = "Eat"; break;
                    case 4: estadoAtual = "TakeStone"; break;
                    default: estadoAtual = "Exploring"; break;
                }
                Bots.PegarBot(this.getName()).estadoAtual = estadoAtual;

                vidaAntes = vidaAgora;
                mobsAntes = mobsAgora;
                ultimaAcao = acao;
                madeirasAntes = madeirasAgora;
                fomeAntes = fomeAgora;
                pedrasAntes = pedrasAgora;
            }
        }
    }

    // ==========================================
    // INTERCEPTADORES E HANDLERS DE REDE (PACKETS)
    // ==========================================

    public static class NetHandlerPirata extends NetHandlerPlayServer {
        public NetHandlerPirata(MinecraftServer server, NetworkManager networkM, EntityPlayerMP player) {
            super(server, networkM, player);
        }
        @Override public void update() {}
        @Override public void sendPacket(Packet<?> packet) {}
        @Override public void disconnect(ITextComponent reason) {}
    }

    // ==========================================
    // SISTEMA DE LOGS E GERENCIAMENTO DE SPAWN
    // ==========================================

    public static void print(EntityPlayerMP jogador, String message) {
        if (jogador != null) {
            jogador.sendMessage(new TextComponentString(message));
        }
    }

    /**
     * Injeta e inicializa dinamicamente a entidade estruturada do Fake Player no servidor.
     */
    public static void criarbot(MinecraftServer servidor, WorldServer mundo, String nome, ICommandSender commander) {
        EntityPlayerMP playerAtual = null;

        if (commander != null && commander.getCommandSenderEntity() instanceof EntityPlayerMP) {
            playerAtual = (EntityPlayerMP) commander.getCommandSenderEntity();
        }

        UUID botUUID = UUID.randomUUID();
        GameProfile botProfile = new GameProfile(botUUID, nome);

        botProfile.getProperties().removeAll("assets/aibot/textures");
        String skinValue = "ewogICJ0aW1lc3RhbXAiIDogMTc4MTQwNDE0MDg4MCwKICAicHJvZmlsZUlkIiA6ICIyOGQyZDFmZDEyNGY0NGMyOGYxZDgwNDY4NGFkOTA2ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJzcGlmZnRvcGlhNyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80ODI1OGUzZWFiNTFlODg5YmZiOGI1ZWI1YWRkZDc0N2U3ZTFjYjVlNWZmMTNjMTJmN2M0YzM3NTY5ZWQ0MjkwIgogICAgfQogIH0KfQ==";
        String skinSignature = "MAkHD8KHWxQ0Ex8pkubhTAtTjDWduJ+APUbPrJUavjvDrkiLRaKsJUheModLv++krcrDIpAC4cBq8p2L9j0F5RSyjomSjJ2PiNMppS6AlvkKNuREjZbwEz7/mJCnvCBkxQFJq73Fnk3cyyuXjNYSvLNQoALrxBlIeRnUQ6atwQ47Yq2Pjss659CBNYMPW8tqkNX1QbafusCBT2r8fUEY2fAj8ze4V+q7Dw1W0Vkh6ueJIBUfalXEoWYiCXVH5FFrou1kv/x69TrsV7mVpgbM0eaznb34dkHMFt39Gdbw8lNO6k0zlg7mWrqOU/6y4zlBxC4SI+/wWltx0Ct6rnkatySzIuZ6qmiVlUottFz5wD/njDmZcEAwNzr3q+MzLnHvVjtQiw6HM6gaJ0GAwrsqK+uGPiebCkDWRalnbNl5tClGQP0j4/l/UgiDj/pfYAKJia1VudPRgcys6UhnXV7jMT8Hr0C9rl9C7vJQc+mp/RGF34uuNsmNCpovCkhxDrS4tQkNL7q7BOw1p25Q+bbENKmtmRiXwZXHCV+GKIgwsLRcCNZ+A+ozWSjoUDoSl/diTiuCqwQzQwib8p1YaZJgtR4KdwEetchElWn1JKblFsTRIxrgfmLhpXUJP+SqR0xdv2lVNvQ3PZkmkWg3u2xBWEQul2dLNaO4E+UKvmnXB+s=";

        botProfile.getProperties().put("assets/aibot/textures", new Property("assets/aibot/textures", skinValue, skinSignature));

        PlayerInteractionManager interacao = new PlayerInteractionManager(mundo);
        EntityPlayerSigma bot = new EntityPlayerSigma(servidor, mundo, botProfile, interacao);

        NetworkManager networkpirata = new NetworkManager(SERVERBOUND) {
            @Override public void setConnectionState(EnumConnectionState state) {}
            @Override public void channelActive(ChannelHandlerContext ctx) throws Exception {}
            @Override public boolean isChannelOpen() { return true; }
            @Override public void sendPacket(Packet<?> packet) {}
            @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 25565); }
        };

        bot.connection = new NetHandlerPirata(servidor, networkpirata, bot);

        if (playerAtual != null) {
            BlockPos commanderPos = playerAtual.getPosition();
            bot.setPositionAndUpdate(commanderPos.getX(), commanderPos.getY() + 1, commanderPos.getZ());
        } else {
            BlockPos spawnPos = mundo.getSpawnPoint();
            bot.setPositionAndUpdate(spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ());
        }

        SPacketPlayerListItem pacoteTab = new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, bot);
        servidor.getPlayerList().sendPacketToAllPlayers(pacoteTab);
        servidor.getPlayerList().getPlayers().add(bot);
        mundo.spawnEntity(bot);

        if (playerAtual != null) {
            print(playerAtual, nome + " entrou no jogo.");
            print(playerAtual, "§aBot '" + nome + "' spawnado com sucesso!");
        }
        if (!Bots.bots.contains(bot)) {
            Bots.bots.add(bot);
        }
    }

    // ==========================================
    // MÉTODOS SOBREPOSTOS DO COMANDO NATIVO MC
    // ==========================================

    @Override
    public String getName() {
        return "zenithplayer";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/zenithplayer [nome] - Spawna um clone de jogador";
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP playerAtual = null;
        String[] estados = {"Attack", "Minning", "CraftTools", "Defense", "Stand", "Eat", "Towering", "Exploring", "TakeWood", "TakeItems"};

        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP) {
            playerAtual = (EntityPlayerMP) sender.getCommandSenderEntity();
        }

        String nomedobot = null;
        String config = "false";
        String all = "false";

        if (args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            nomedobot = args[0];
            if (args.length > 1 && args[1] != null && !args[1].trim().isEmpty()) {
                config = args[1];
                if (args.length > 2 && args[2] != null && !args[2].trim().isEmpty()) {
                    all = args[2];
                }
            }
        }

        if (nomedobot == null || nomedobot.trim().isEmpty()) {
            String nomeEsperado = "SigmaBot_" + ((int) (Math.random() * 100));
            if (Bots.PegarBot(nomeEsperado) != null) {
                nomeEsperado = "SigmaBot_" + ((int) (Math.random() * 1000));
            }
            nomedobot = nomeEsperado;
        }

        if (!"false".equalsIgnoreCase(config) && !config.trim().isEmpty()) {
            if (Bots.PegarBot(nomedobot) != null) {
                for (String state : estados) {
                    if (state.equalsIgnoreCase(config)) {
                        Bots.PegarBot(nomedobot).estadoAtual = config;
                        if (playerAtual != null) {
                            playerAtual.sendMessage(new TextComponentString(nomedobot + " está " + config));
                        }
                        return;
                    }
                }
            }
        }

        if ("true".equalsIgnoreCase(all)) {
            for (EntityPlayerSigma bot : Bots.bots) {
                for (String state : estados) {
                    if (state.equalsIgnoreCase(config)) {
                        Bots.PegarBot(bot.getName()).estadoAtual = config;
                        if (playerAtual != null) {
                            playerAtual.sendMessage(new TextComponentString(bot.getName() + " está " + config));
                        }
                    }
                }
            }
            return;
        } else if ("exit".equalsIgnoreCase(all)) {
            for (EntityPlayerSigma bot : Bots.bots) {
                bot.setDead();
                SPacketPlayerListItem pacoteRemove = new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, bot);
                bot.getServer().getPlayerList().sendPacketToAllPlayers(pacoteRemove);
                bot.getServer().getPlayerList().getPlayers().remove(bot);
                if (playerAtual != null) {
                    print(playerAtual, bot.getName() + " saiu do jogo");
                }
            }
            Bots.bots.clear();
            return;
        }

        if (PegarWorldServer.ServerWorld != null) {
            if (playerAtual != null) {
                criarbot(server, playerAtual.getServerWorld(), nomedobot, sender);
            } else {
                WorldServer worldServer = PegarWorldServer.ServerWorld;
                criarbot(server, worldServer, nomedobot, sender);
            }
        } else {
            if (playerAtual != null) {
                print(playerAtual, "Configure o mundo primeiro!");
            }
        }
    }
}