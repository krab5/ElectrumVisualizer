/* Alloy Analyzer 4 -- Copyright (c) 2006-2009, Felix Chang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package edu.mit.csail.sdg.alloy4graph;

import static edu.mit.csail.sdg.alloy4graph.Artist.getBounds;
import static edu.mit.csail.sdg.alloy4graph.Graph.esc;
import static edu.mit.csail.sdg.alloy4graph.Graph.selfLoopA;
import static edu.mit.csail.sdg.alloy4graph.Graph.selfLoopGL;
import static edu.mit.csail.sdg.alloy4graph.Graph.selfLoopGR;
import static java.lang.StrictMath.PI;
import static java.lang.StrictMath.atan2;
import static java.lang.StrictMath.cos;
import static java.lang.StrictMath.sin;
import static java.lang.StrictMath.toRadians;

import java.awt.Color;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Mutable; represents a graphical edge.
 *
 * <p>
 * <b>Thread Safety:</b> Can be called only by the AWT event thread.
 */
public final strictfp class GraphEdge extends AbstractGraphElement {

    // =============================== adjustable options ===========================================================================
    /**
     * The angle (in radian) to fan out the arrow head, if the line is not bold.
     */
    private final double smallFan = toRadians(16);

    /**
     * The angle (in radian) to fan out the arrow head, if the line is bold.
     */
    private final double bigFan = toRadians(32);

    // =============================== cached for performance efficiency ============================================================
    /**
     * The maximum ascent and descent. We deliberately do NOT make this field
     * "static" because only AWT thread can call Artist.
     */
    private final int ad = Artist.getMaxAscentAndDescent();

    // =============================== fields =======================================================================================
    /**
     * a user-provided annotation that will be associated with this edge (all
     * edges with same group will be highlighted together)
     */
    public final Object group;

    /**
     * The "from" node; must stay in sync with AbstractGraphNode.ins and
     * AbstractGraphNode.outs and AbstractGraphNode.selfs
     */
    private AbstractGraphNode a; // [N7-G.Dupont] An edge can connect either ports or nodes

    /**
     * The "to" node; must stay in sync with AbstractGraphNode.ins and
     * AbstractGraphNode.outs and AbstractGraphNode.selfs
     */
    private AbstractGraphNode b; // [N7-G.Dupont] An edge can connect either ports or nodes

    /**
     * The label (can be ""); NOTE: label will be drawn only if the start node
     * is not a dummy node.
     */
    private final String label;

    /**
     * Whether to draw an arrow head on the "from" node; default is false.
     */
    private boolean ahead = false;

    /**
     * Whether to draw an arrow head on the "to" node; default is true.
     */
    private boolean bhead = true;

    /**
     * The edge weight; default is 1; always between 1 and 10000 inclusively.
     */
    private int weight = 1;

    /**
     * The location and size of the label box; initially (0, 0, label.width,
     * label.height)
     */
    private final AvailableSpace.Box labelbox;

    /**
     * The actual path corresponding to this edge; initially null until it's
     * computed.
     */
    private Curve path = null;

    // =========================================================================s====================================================
    /**
     * Construct an edge from "from" to "to" with the given arrow head settings,
     * then add the edge to the graph.
     */
    GraphEdge(AbstractGraphNode from, AbstractGraphNode to, Graph parent, Object uuid, String label, boolean drawArrowHeadOnFrom, boolean drawArrowHeadOnTo, DotStyle style, Color color, Object group) {
        super(parent, uuid);
        if (group instanceof GraphNode) {
            throw new IllegalArgumentException("group cannot be a GraphNode");
        }
        if (group instanceof GraphEdge) {
            throw new IllegalArgumentException("group cannot be a GraphEdge");
        }
        if (group == null) {
            group = new Object();
        }
        a = from;
        b = to;
        if (a == b) {
            a.selfs.add(this);
        } else {
            a.outs.add(this);
            b.ins.add(this);
        }
        
        /**
         * [N7] @Louis Fauvarque
         * Added the distinction between the normal edges and the edges with ports
         */
        if (!(a instanceof GraphPort || b instanceof GraphPort)) {
            a.graph.edgelist.add(this);
        } else {
            a.graph.portEdgeList.add(this);
        }
        this.group = group;
        this.label = (label == null) ? "" : label;
        this.ahead = drawArrowHeadOnFrom;
        this.bhead = drawArrowHeadOnTo;
        if (style != null) {
            this.setStyle(style);
        }
        if (color != null) {
            this.setColor(color);
        } else { //[N7-G.Dupont] Default color
            this.setColor(Color.BLACK);
        }
        if (this.label.length() > 0) {
            Rectangle2D box = getBounds(false, label);
            labelbox = new AvailableSpace.Box(0, 0, (int) box.getWidth(), (int) box.getHeight());
        } else {
            labelbox = new AvailableSpace.Box(0, 0, 0, 0);
        }
    }

    /**
     * Construct an edge from "from" to "to", then add the edge to the graph.
     */
    public GraphEdge(AbstractGraphNode from, AbstractGraphNode to, Graph parent, Object uuid, String label, Object group) {
        this(from, to, parent, uuid, label, false, true, null, null, group);
    }

    //[N7-R.Bossut, M.Quentin]
    /**
     * Create a copy of the given edge, between nodes from and to.
     */
    public GraphEdge(GraphEdge toBeCopied, AbstractGraphNode to, AbstractGraphNode from) {
        //Copy attributes.
        super(toBeCopied.graph, toBeCopied.uuid);
        this.group = toBeCopied.group;
        this.label = toBeCopied.label;
        this.ahead = toBeCopied.ahead;
        this.bhead = toBeCopied.bhead;
        this.setStyle(toBeCopied.getStyle());
        this.setColor(toBeCopied.getColor());
        //Create a new labelebox.
        if (this.label.length() > 0) {
            Rectangle2D box = getBounds(false, label);
            labelbox = new AvailableSpace.Box(0, 0, (int) box.getWidth(), (int) box.getHeight());
        } else {
            labelbox = new AvailableSpace.Box(0, 0, 0, 0);
        }
        //Set from and to nodes. 
        this.a = from;
        this.b = to;
        //Add this edge to nodes.
        if (a == b) {
            a.selfs.add(this);
        } else {
            a.outs.add(this);
            b.ins.add(this);
        }
        //Add this to the graph.
        a.graph.edgelist.add(this);
    }

    /**
     * Returns the "from" node.
     */
    public AbstractGraphNode a() {
        return a;
    }

    /**
     * Returns the "to" node.
     */
    public AbstractGraphNode b() {
        return b;
    }

    /**
     * Swaps the "from" node and "to" node.
     */
    void reverse() {
        if (a == b) {
            return;
        }
        a.outs.remove(this);
        b.ins.remove(this);
        a.ins.add(this);
        b.outs.add(this);
        AbstractGraphNode x = a;
        a = b;
        b = x;
    }

    /**
     * Changes the "to" node to the given node.
     */
    void change(AbstractGraphNode newTo) {
        if (a == b) {
            a.selfs.remove(this);
        } else {
            a.outs.remove(this);
            b.ins.remove(this);
        }
        b = newTo;
        if (a == b) {
            a.selfs.add(this);
        } else {
            a.outs.add(this);
            b.ins.add(this);
        }
    }

    /**
     * Returns the a node of the edge.
     */
    public AbstractGraphNode getA() {
        return a;
    }

    /**
     * Returns the a node of the edge.
     */
    public AbstractGraphNode getB() {
        return b;
    }

    /**
     * Return the X coordinate of the top-left corner of the label box.
     */
    public int getLabelX() {
        return labelbox.x;
    }

    /**
     * Return the Y coordinate of the top-left corner of the label box.
     */
    public int getLabelY() {
        return labelbox.y;
    }

    /**
     * Return the width of the label box.
     */
    public int getLabelW() {
        return labelbox.w;
    }

    /**
     * Return the height of the label box.
     */
    public int getLabelH() {
        return labelbox.h;
    }

    /**
     * Returns the edge weight (which is always between 1 and 10000
     * inclusively).
     */
    public int weight() {
        return weight;
    }

    /**
     * Returns true if we will draw an arrow head on the "from" node.
     */
    public boolean ahead() {
        return ahead;
    }

    /**
     * Returns true if we will draw an arrow head on the "to" node.
     */
    public boolean bhead() {
        return bhead;
    }

    /**
     * Returns the label on this edge.
     */
    public String label() {
        return label;
    }

    /**
     * Sets the edge weight between 1 and 10000.
     */
    public GraphEdge set(int weightBetween1And10000) {
        if (weightBetween1And10000 < 1) {
            weightBetween1And10000 = 1;
        }
        if (weightBetween1And10000 > 10000) {
            weightBetween1And10000 = 10000;
        }
        weight = weightBetween1And10000;
        return this;
    }

    /**
     * Sets whether we will draw an arrow head on the "from" node, and whether
     * we will draw an arrow head on the "to" node.
     */
    public GraphEdge set(boolean from, boolean to) {
        this.ahead = from;
        this.bhead = to;
        return this;
    }

    /**
     * Sets the line style.
     */
    public GraphEdge set(DotStyle style) {
        if (this.getStyle() != null) {
            this.setStyle(style);
        }
        return this;
    }

    /**
     * Sets the line color.
     */
    public GraphEdge set(Color color) {
        if (this.getColor() != null) {
            this.setColor(color);
        }
        return this;
    }

    /**
     * Returns the current path; if the path was not yet assigned, it returns a
     * straight line from "from" node to "to" node.
     */
    Curve path() {
        if (path == null) {
            resetPath();
        }
        return path;
    }

    /**
     * Reset the path as a straightline from the center of the "from" node to
     * the center of the "to" node.
     */
    void resetPath() {
        /**
         * [N7] Louis Fauvarque Adds the case of GraphPorts
         */
        double ax = a.x(), ay = a.y();
        if (!(a instanceof GraphNode)) {
            ax = ((GraphPort) a).node.x() + ((GraphPort) a).x();
            ay = ((GraphPort) a).node.y() + ((GraphPort) a).y();
        }
        if (a == b) {
            double w = 0;
            for (int n = a.selfs.size(), i = 0; i < n; i++) {
                if (i == 0) {
                    w = a.getWidth() / 2 + selfLoopA;
                } else {
                    w = w + getBounds(false, a.selfs.get(i - 1).label()).getWidth() + selfLoopGL + selfLoopGR;
                }
                GraphEdge e = a.selfs.get(i);
                if (e != this) {
                    continue;
                }
                double h = a.getHeight() / 2D * 0.7D, k = 0.55238D, wa = (a.getWidth() / 2.0D), wb = w - wa;
                e.path = new Curve(ax, ay);
                e.path.cubicTo(ax, ay - k * h, ax + wa - k * wa, ay - h, ax + wa, ay - h);
                e.path.cubicTo(ax + wa + k * wb, ay - h, ax + wa + wb, ay - k * h, ax + wa + wb, ay);
                e.path.cubicTo(ax + wa + wb, ay + k * h, ax + wa + k * wb, ay + h, ax + wa, ay + h);
                e.path.cubicTo(ax + wa - k * wa, ay + h, ax, ay + k * h, ax, ay);
                e.labelbox.x = (int) (ax + w + selfLoopGL);
                e.labelbox.y = (int) (ay - getBounds(false, e.label()).getHeight() / 2);
                break;
            }
        } else {
            int i = 0, n = 0;
            for (GraphEdge e : a.outs) {
                if (e == this) {
                    i = n++;
                } else if (e.b == b) {
                    n++;
                }
            }

            AbstractGraphNode in = b, out = a;
            if (b instanceof GraphPort) {
                in = ((GraphPort) b).node;
            }
            if (a instanceof GraphPort) {
                out = ((GraphPort) a).node;
            }
            double cx = in.relativeX(out.getFather());
            double cy = in.relativeY(out.getFather());
            if (b instanceof GraphPort) {
                cx += b.x();
                cy += b.y();
            }

            double bx = (ax + cx) / 2, by = (ay + cy) / 2;
            path = new Curve(ax, ay);
            if (n > 1 && (n & 1) == 1) {
                if (i < n / 2) {
                    bx = bx - (n / 2 - i) * 10;
                } else if (i > n / 2) {
                    bx = bx + (i - n / 2) * 10;
                }
                path.lineTo(bx, by).lineTo(cx, cy);
            } else if (n > 1) {
                if (i < n / 2) {
                    bx = bx - (n / 2 - i) * 10 + 5;
                } else {
                    bx = bx + (i - n / 2) * 10 + 5;
                }
                path.lineTo(bx, by).lineTo(cx, cy);
            } else {
                path.lineTo(cx, cy);
            }
        }
    }

    /**
     * Given that this edge is already well-laid-out, this method moves the
     * label hoping to avoid/minimize overlap.
     */
    void repositionLabel(AvailableSpace sp) {
        if (label.length() == 0 || a == b) {
            return; // labels on self-edges are already re-positioned by GraphEdge.resetPath()
        }

        final int gap = this.getStyle() == DotStyle.BOLD ? 4 : 2; // If the line is bold, we need to shift the label to the right a little bit

        boolean failed = false;
        Curve p = path;
        for (AbstractGraphNode a = this.a; a.shape() == null;) {
            GraphEdge e = a.ins.get(0);
            a = e.a;
            p = e.path().join(p);
        }
        for (AbstractGraphNode b = this.b; b.shape() == null;) {
            GraphEdge e = b.outs.get(0);
            b = e.b;
            p = p.join(e.path());
        }
        for (double t = 0.5D;; t = t + 0.05D) {
            if (t >= 1D) {
                failed = true;
                t = 0.7D;
            }
            double x1 = p.getX(t), y = p.getY(t), x2 = p.getXatY(y + labelbox.h, t, 1D, x1);
            int x = (int) (x1 < x2 ? x2 + gap : x1 + gap);
            if (failed || sp.ok(x, (int) y, labelbox.w, labelbox.h)) {
                sp.add(labelbox.x = x, labelbox.y = (int) y, labelbox.w, labelbox.h);
                return;
            }
            double t2 = 1D - t;
            x1 = p.getX(t2);
            y = p.getY(t2);
            x2 = p.getXatY(y + labelbox.h, t2, 1D, x1);
            x = (int) (x1 < x2 ? x2 + gap : x1 + gap);
            if (sp.ok(x, (int) y, labelbox.w, labelbox.h)) {
                sp.add(labelbox.x = x, labelbox.y = (int) y, labelbox.w, labelbox.h);
                return;
            }
        }
    }

    /**
     * Positions the arrow heads of the given edge properly.
     */
    void layout_arrowHead() {
        Curve c = path();
        if (b.shape() != null) {
            double in = 1D, out = (a == b ? 0.5D : 0D);
            boolean takein = false;
            while (StrictMath.abs(out - in) > 0.0001D) {
                double t = (in + out) / 2;
                if (b instanceof GraphNode && a instanceof GraphNode) {
                    takein = ((GraphNode)b).contains(c.getX(t), c.getY(t), (GraphNode)a);
                } else {
                    takein = b.contains(c.getX(t), c.getY(t));
                }
                if (takein) {
                    in = t;
                } else {
                    out = t;
                }
            }
            c.chopEnd(in);
        }
    }
    
    /**
     * Positions the arrow tails of the given edge properly.
     */
    void layout_arrowTail() {
        Curve c = path();
        if (a.shape() != null) {
            double in = 0D, out = 1D;
            while (StrictMath.abs(out - in) > 0.0001D) {
                double t = (in + out) / 2;
                if (a.contains(c.getX(t), c.getY(t))) {
                    in = t;
                } else {
                    out = t;
                }
            }
            c.chopStart(in);
        }
    }
    
    /**
     * Assuming this edge's coordinates have been properly assigned, and given
     * the current zoom scale, draw the edge.
     */
    void draw(Artist gr, double scale, GraphEdge highEdge, Object highGroup) {
        if (this.getStyle() == DotStyle.BLANK)
            return;
        
        if (path == null)
            resetPath();
        int top = 0, left = 0;
                    
        if (a instanceof GraphNode && ((GraphNode)a).getFather() != null) {
            top = a.getFather().getSubGraph().getTop();
            left = a.getFather().getSubGraph().getLeft();
        } else {
            top = a.graph.getTop(); left = a.graph.getLeft();
        }
        
        int transX=-left, transY=-top;
        if (this.highlight()) {
            AbstractGraphNode gn = a;
            if (gn.getFather() != null) {
                transX = 0; transY = 0;
                while (gn.getFather() != null) {
                    gn = gn.getFather();
                    transX += gn.x();
                    transY += gn.y();
                }
                transX -= gn.graph.getLeft();
                transY -= gn.graph.getTop();
            }
        }
        
        gr.translate(transX, transY);
        
        if (highEdge == this) {
            gr.setColor(this.getColor());
            gr.set(DotStyle.BOLD, scale);
        } else if ((highEdge == null && highGroup == null) || highGroup == group) {
            gr.setColor(this.getColor());
            gr.set(this.getStyle(), scale);
        } else {
            gr.setColor(Color.LIGHT_GRAY);
            gr.set(this.getStyle(), scale);
        }
        
        if (getNeedHighlight()) {
            gr.set(DotStyle.DASHED, scale);
        } else {
            gr.set(this.getStyle(), scale);
        }
        
        if (a == b) {
            gr.draw(path);
        } else {
            // Concatenate this path and its connected segments into a single VizPath object, then draw it
            Curve p=null;
            GraphEdge e=this;
            while(e.a.shape() == null)
                e = e.a.ins.get(0); // Let e be the first segment of this chain of connected segments
            while(true) {
                //e.layout_arrowHead(); e.layout_arrowTail();
                p = (p==null) ? e.path : p.join(e.path);
                if (e.b.shape()!=null) break;
                e = e.b.outs.get(0);
            }
            layout_arrowHead();
            layout_arrowTail();
            gr.drawSmoothly(p);
        }
        
        gr.set(DotStyle.SOLID, scale);
        
        gr.translate(-transX, -transY);
        
        if (highEdge==null && highGroup==null && label.length()>0) {
            drawLabel(gr, getColor(), null);
        }
        drawArrowhead(gr, scale, highEdge, highGroup);
    }

    /**
     * Assuming this edge's coordinates have been properly assigned, and given
     * the desired color, draw the edge label.
     */
    void drawLabel(Artist gr, Color color, Color erase) {
        if (this.getStyle() == DotStyle.BLANK)
            return;

        if (label.length() > 0) {
            final int top = a.graph.getTop(), left = a.graph.getLeft();

            int transX=-left, transY=-top;
            if (this.highlight()) {
                AbstractGraphNode gn = a;
                while (gn.getFather() != null) {
                    gn = gn.getFather();
                    transX += gn.x();
                    transY += gn.y();
                }
                transX -= gn.graph.getLeft();
                transY -= gn.graph.getTop();
            }
            
            gr.translate(transX, transY);

            if (erase != null && a != b) {
                Rectangle2D.Double rect = new Rectangle2D.Double(labelbox.x, labelbox.y, labelbox.w, labelbox.h);
                gr.setColor(erase);
                gr.draw(rect, true);
            }
            gr.setColor(color);
            gr.drawString(label, labelbox.x, labelbox.y + Artist.getMaxAscent());

            gr.translate(-transX, -transY);

            return;
        }
    }

    /**
     * Assuming this edge's coordinates have been assigned, and given the
     * current zoom scale, draw the arrow heads.
     */
    private void drawArrowhead(Artist gr, double scale, GraphEdge highEdge, Object highGroup) {
        if (this.getStyle() == DotStyle.BLANK) // Modified @Louis Fauvarque
            return;
        
        // Set the arrow head's size
        // [N7] @Julien Richer => doubled tipLength
        final double tipLength = ad * 0.6D * 1.5D;

        int top = a.graph.getTop(), left = a.graph.getLeft();

        int transX=0, transY=0;
        if (this.highlight() && b instanceof GraphNode) {
            AbstractGraphNode bgn = b;
            if (bgn.getFather() != null) {
                while (bgn.getFather() != null) {
                    bgn = bgn.getFather();
                    transX += bgn.x();
                    transY += bgn.y();
                }
                transX = transX - bgn.graph.getLeft() + bgn.graph.getTop();
                transY = transY - bgn.graph.getTop() + bgn.graph.getLeft();
            } else if (a instanceof GraphNode && (((GraphNode) a).getFather()) != null) {
                GraphNode agn = (GraphNode) a;
                transX = agn.getFather().x() - agn.getFather().graph.getLeft() + left;
                transY = agn.getFather().y() - agn.getFather().graph.getTop() + top;
            }
            gr.translate(transX, transY);
        }
            
        // Check to see if this edge is highlighted or not
        double fan = (this.getStyle() == DotStyle.BOLD ? bigFan : smallFan);
        if (highEdge == this) {
            fan = bigFan;
            gr.setColor(this.getColor());
            gr.set(DotStyle.BOLD, scale);
        } else if ((highEdge == null && highGroup == null) || highGroup == group) {
            gr.setColor(this.getColor());
            gr.set(this.getStyle(), scale);
        } else {
            gr.setColor(Color.LIGHT_GRAY);
            gr.set(this.getStyle(), scale);
        }
        for (GraphEdge e = this;; e = e.b.outs.get(0)) {
            if ((e.ahead && e.a.shape() != null) || (e.bhead && e.b.shape() != null)) {
                Curve cv = e.path();
                if (e.ahead && e.a.shape() != null) {
                    CubicCurve2D.Double bez = cv.list.get(0);
                    double ax = bez.x1, ay = bez.y1, bx = bez.ctrlx1, by = bez.ctrly1;
                    double t = PI + atan2(ay - by, ax - bx);
                    double gx1 = ax + tipLength * cos(t - fan), gy1 = ay + tipLength * sin(t - fan);
                    double gx2 = ax + tipLength * cos(t + fan), gy2 = ay + tipLength * sin(t + fan);
                    GeneralPath gp = new GeneralPath();
                    gp.moveTo((float) (gx1 - left), (float) (gy1 - top));
                    gp.lineTo((float) (ax - left), (float) (ay - top));
                    gp.lineTo((float) (gx2 - left), (float) (gy2 - top));
                    gp.closePath();
                    gr.draw(gp, true);
                }
                if (e.bhead && e.b.shape() != null) {
                    CubicCurve2D.Double bez = cv.list.get(cv.list.size() - 1);
                    double bx = bez.x2, by = bez.y2, ax = bez.ctrlx2, ay = bez.ctrly2;
                    double t = PI + atan2(by - ay, bx - ax);
                    double gx1 = bx + tipLength * cos(t - fan), gy1 = by + tipLength * sin(t - fan);
                    double gx2 = bx + tipLength * cos(t + fan), gy2 = by + tipLength * sin(t + fan);
                    GeneralPath gp = new GeneralPath();
                    gp.moveTo((float) (gx1 - left), (float) (gy1 - top));
                    gp.lineTo((float) (bx - left), (float) (by - top));
                    gp.lineTo((float) (gx2 - left), (float) (gy2 - top));
                    gp.closePath();
                    gr.draw(gp, true);
                }
            }
            if (e.b.shape() != null) {
                break;
            }
        }
        
        if (this.highlight()) {
            gr.translate(-transX, -transY);
        }
    }

    /**
     * Returns a DOT representation of this edge (or "" if the start node is a
     * dummy node)
     */
    @Override
    public String toString() {
        AbstractGraphNode a = this.a, b = this.b;
        if (a.shape() == null) {
            return ""; // This means this edge is virtual
        }
        while (b.shape() == null) {
            b = b.outs.get(0).b;
        }
        String color = Integer.toHexString(this.getColor().getRGB() & 0xFFFFFF);
        while (color.length() < 6) {
            color = "0" + color;
        }
        StringBuilder out = new StringBuilder();

        if (a instanceof GraphNode) {
            out.append("\"N" + ((GraphNode) a).pos() + "\"");
        } else {
            GraphPort ap = (GraphPort) a;
            out.append("\"P[" + ap.getOrientation() + "," + ap.getOrder() + "]\"");
        }

        out.append(" -> ");

        if (b instanceof GraphNode) {
            out.append("\"N" + ((GraphNode) b).pos() + "\"");
        } else {
            GraphPort bp = (GraphPort) b;
            out.append("\"P[" + bp.getOrientation() + "," + bp.getOrder() + "]\"");
        }

        out.append(" [");
        out.append("uuid = \"" + (uuid == null ? "" : esc(uuid.toString())) + "\"");
        out.append(", color = \"#" + color + "\"");
        out.append(", fontcolor = \"#" + color + "\"");
        out.append(", style = \"" + this.getStyle().getDotText() + "\"");
        out.append(", label = \"" + esc(label) + "\"");
        out.append(", dir = \"" + (ahead && bhead ? "both" : (bhead ? "forward" : "back")) + "\"");
        out.append(", weight = \"" + weight + "\"");
        out.append("]\n");
        return out.toString();
    }
}
