package es.monsteraltech.skincare_tfm.body.mole

import androidx.recyclerview.widget.DiffUtil

class MoleDiffCallback(
    private val oldMoleList: List<Mole>,
    private val newMoleList: List<Mole>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldMoleList.size

    override fun getNewListSize() = newMoleList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Compara si los items son los mismos (por ejemplo, si tienen el mismo ID)
        return oldMoleList[oldItemPosition].title == newMoleList[newItemPosition].title
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Compara si el contenido de los items es el mismo
        return oldMoleList[oldItemPosition] == newMoleList[newItemPosition]
    }
}