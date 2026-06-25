import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;

public class ASTNode {
    private String label;
    private List<ASTNode> children = new ArrayList<>();

    public ASTNode(String label) {
        this.label = label;
    }

    public void addChild(ASTNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public String getLabel() {
        return label;
    }

    public List<ASTNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return label;
    }

    // Convert abstract syntax tree to DefaultMutableTreeNode for Swing's JTree
    public DefaultMutableTreeNode toSwingTree() {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(this.label);
        for (ASTNode child : children) {
            swingNode.add(child.toSwingTree());
        }
        return swingNode;
    }
}
