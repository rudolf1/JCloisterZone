package com.jcloisterzone.ui.grid.layer;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.Tile;
import com.jcloisterzone.feature.Farm;
import com.jcloisterzone.feature.Feature;
import com.jcloisterzone.feature.visitor.FeatureVisitor;
import com.jcloisterzone.figure.Barn;
import com.jcloisterzone.figure.Follower;
import com.jcloisterzone.figure.Meeple;
import com.jcloisterzone.ui.grid.GridPanel;
import com.jcloisterzone.ui.resources.ResourceManager;

public class FarmHintsLayer extends AbstractGridLayer {

    private static final int FULL_SIZE = 300;
    private static final AlphaComposite HINT_ALPHA_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .3f);

    private final List<FarmHint> hints = new ArrayList<>();

    static public class TextureFactory {

        private final int squareSize;
        private int seq;

        public TextureFactory(int squareSize) {
            this.squareSize = squareSize;
        }

        public TexturePaint create(Color c) {
            switch (++seq) {
            case 6: seq = 0;
            case 0: return createDiagonalUp(c);
            case 1: return createVertical(c);
            case 2: return createDiagonalCheck(c);
            case 3: return createDiagonalDown(c);
            case 4: return createHorizontal(c);
            default: return createCheck(c);
            }
        }

        private TexturePaint createDiagonalUp(Color c) {
            BufferedImage bi = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setColor(c);
            g2.fill(new Polygon(new int[] {16, 32, 32}, new int[] {32, 16, 32}, 3));
            g2.fill(new Polygon(new int[] {0, 16, 32, 0}, new int[] {16, 0, 0, 32}, 4));
            return new TexturePaint(bi, new Rectangle(0, 0, 32, 32));
        }

        private TexturePaint createDiagonalDown(Color c) {
            BufferedImage bi = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setColor(c);
            g2.fill(new Polygon(new int[] {0, 0, 16}, new int[] {32, 16, 32}, 3));
            g2.fill(new Polygon(new int[] {0, 16, 32, 32}, new int[] {0, 0, 16, 32}, 4));
            return new TexturePaint(bi, new Rectangle(0, 0, 32, 32));
        }

        private TexturePaint createVertical(Color c) {
            BufferedImage bi = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setColor(c);
            g2.fillRect(0, 0, 11, 22);
            return new TexturePaint(bi, new Rectangle(0, 0, 22, 22));
        }

        private TexturePaint createHorizontal(Color c) {
            BufferedImage bi = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setColor(c);
            g2.fillRect(0, 0, 22, 11);
            return new TexturePaint(bi, new Rectangle(0, 0, 22, 22));
        }

        private TexturePaint createDiagonalCheck(Color c) {
            BufferedImage bi = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setColor(c);
            g2.fill(new Polygon(new int[] {10, 20, 10, 0}, new int[] {0, 10, 20, 10}, 4));
            return new TexturePaint(bi, new Rectangle(0, 0, 20, 20));
        }

        private TexturePaint createCheck(Color c) {
            BufferedImage bi = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setColor(c);
            g2.fillRect(0, 0, 11, 11);
            g2.fillRect(11, 11, 11, 11);
            return new TexturePaint(bi, new Rectangle(0, 0, 22, 22));
        }
    }

    public FarmHintsLayer(GridPanel gridPanel) {
        super(gridPanel);
        refreshHints();
    }

    @Override
    public void paint(Graphics2D g2) {
        Composite old = g2.getComposite();
        g2.setComposite(HINT_ALPHA_COMPOSITE);
        int sqSize = getSquareSize();
        Double scale = sqSize == FULL_SIZE ? null : (double) sqSize / FULL_SIZE;
        TextureFactory textures = new TextureFactory(sqSize);

        for (FarmHint fh : hints) {
            if (fh.scaledArea == null) {
                if (scale == null) {
                    fh.scaledArea = fh.area;
                } else {
                    fh.scaledArea = fh.area.createTransformedArea(AffineTransform.getScaleInstance(scale, scale));
                }
            }
            g2.setPaint(textures.create(fh.color));
            g2.fill(transformArea(fh.scaledArea, fh.position));
        }
        g2.setPaint(null);
        g2.setComposite(old);
    }

    @Override
    public int getZIndex() {
        return 10;
    }

    public void refreshHints() {
        final Map<Position, Map<Location, Area>> areas = new HashMap<>();

        ResourceManager resourceManager = getClient().getResourceManager();
        for (Tile tile : getGame().getBoard().getAllTiles()) {
            Set<Location> farmLocations = new HashSet<>();
            for (Feature f : tile.getFeatures()) {
                if (f instanceof Farm) {
                    farmLocations.add(f.getLocation());
                }
            }
            Map<Location, Area> tileAreas = resourceManager.getFeatureAreas(tile, FULL_SIZE, farmLocations);
            areas.put(tile.getPosition(), tileAreas);
        }

        hints.clear();
        final Set<Feature> processed = new HashSet<>();
        //TODO concurent modification issue !!!
        for (Tile tile : getGame().getBoard().getAllTiles()) {
            for (Feature f : tile.getFeatures()) {
                if (!(f instanceof Farm)) continue;
                if (processed.contains(f)) continue;
                FarmHint fh = f.walk(new FeatureVisitor<FarmHint>() {
                    FarmHint result = new FarmHint(new Area(), null);
                    int x = Integer.MAX_VALUE;
                    int y = Integer.MAX_VALUE;
                    int size = 0;
                    boolean hasCity = false;
                    int[] power = new int[getGame().getAllPlayers().length];

                    @Override
                    public boolean visit(Feature feature) {
                        Farm f = (Farm) feature;
                        processed.add(f);
                        size++;
                        hasCity = hasCity || f.getAdjoiningCities() != null || f.isAdjoiningCityOfCarcassonne();
                        for (Meeple m : f.getMeeples()) {
                            if (m instanceof Follower) {
                                power[m.getPlayer().getIndex()] += ((Follower)m).getPower();
                            }
                            if (m instanceof Barn) {
                                power[m.getPlayer().getIndex()] += 1;
                            }
                        }
                        Position pos = f.getTile().getPosition();
                        if (pos.x < x) {
                            if (x != Integer.MAX_VALUE) result.area.transform(AffineTransform.getTranslateInstance(FULL_SIZE * (x-pos.x), 0));
                            x = pos.x;
                        }
                        if (pos.y < y) {
                            if (y != Integer.MAX_VALUE) result.area.transform(AffineTransform.getTranslateInstance(0, FULL_SIZE * (y-pos.y)));
                            y = pos.y;
                        }
                        Area featureArea = areas.get(pos).get(f.getLocation());
                        featureArea.transform(AffineTransform.getTranslateInstance(FULL_SIZE * (pos.x-x), FULL_SIZE*(pos.y-y)));
                        result.area.add(featureArea);
                        return true;
                    }

                    @Override
                    public FarmHint getResult() {
                        result.position = new Position(x, y);

                        int bestPower = 0;
                        int bestPlayerIndex = -1;
                        for (int i = 0; i < power.length; i++) {
                            if (power[i] == bestPower) {
                                bestPlayerIndex = -1;
                            }
                            if (power[i] > bestPower) {
                                bestPower = power[i];
                                bestPlayerIndex = i;
                            }
                        }
                        if (bestPower == 0) {
                            if (size < 2 || !hasCity) return null; //don't display unimportant farms
                            result.color = Color.DARK_GRAY;
                        } else {
                            if (bestPlayerIndex == -1) { //tie
                                result.color = Color.BLACK;
                            } else {
                                result.color = getClient().getPlayerColor(getGame().getPlayer(bestPlayerIndex));
                            }
                        }

                        return result;
                    }
                });
                if (fh == null) continue; //to small farm
                //fh.area = newArea(stroke.createStrokedShape(fh.area));
                hints.add(fh);
            }
        }
    }

    @Override
    public void zoomChanged(int squareSize) {
        for (FarmHint fh : hints) {
            fh.scaledArea = null;
        }
    }

    static class FarmHint {
        public Area area;
        public Area scaledArea;
        public Position position;
        public Color color;

        public FarmHint(Area area, Position position) {
            this.area = area;
            this.position = position;
        }
    }

}
