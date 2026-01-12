package com.manavoori.muchhatlu.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.manavoori.muchhatlu.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder> {

    public interface Listener {
        void onEdit(@NonNull Map<String, Object> article);
        void onDelete(@NonNull Map<String, Object> article);
    }

    private final List<Map<String, Object>> articles = new ArrayList<>();
    private final Listener listener;

    public ArticleAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_article, parent, false);
        return new ArticleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
        holder.bind(articles.get(position));
    }

    @Override
    public int getItemCount() {
        return articles.size();
    }

    public void submitList(@NonNull List<Map<String, Object>> newArticles) {
        articles.clear();
        articles.addAll(newArticles);
        notifyDataSetChanged();
    }

    class ArticleViewHolder extends RecyclerView.ViewHolder {
        private final TextView textArticleTitle;
        private final TextView textArticleMeta;
        private final TextView textArticleStatus;
        private final TextView badgeFeatured;
        private final TextView badgePinned;
        private final TextView badgeBreaking;
        private final TextView textViews;
        private final TextView textLikes;
        private final TextView textComments;
        private final Button buttonEdit;
        private final Button buttonDelete;

        ArticleViewHolder(@NonNull View itemView) {
            super(itemView);
            textArticleTitle = itemView.findViewById(R.id.textArticleTitle);
            textArticleMeta = itemView.findViewById(R.id.textArticleMeta);
            textArticleStatus = itemView.findViewById(R.id.textArticleStatus);
            badgeFeatured = itemView.findViewById(R.id.badgeFeatured);
            badgePinned = itemView.findViewById(R.id.badgePinned);
            badgeBreaking = itemView.findViewById(R.id.badgeBreaking);
            textViews = itemView.findViewById(R.id.textViews);
            textLikes = itemView.findViewById(R.id.textLikes);
            textComments = itemView.findViewById(R.id.textComments);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }

        void bind(Map<String, Object> article) {
            String title = (String) article.get("title");
            textArticleTitle.setText(title != null ? title : "Untitled");

            // Build meta string
            String author = (String) article.get("authorName");
            Long createdAt = article.get("createdAt") != null ? ((Number) article.get("createdAt")).longValue() : 0;
            String meta = (author != null ? "By " + author : "By Admin");
            if (createdAt > 0) {
                long diff = System.currentTimeMillis() - createdAt;
                long hours = diff / (60 * 60 * 1000);
                if (hours < 1) {
                    meta += " • Just now";
                } else if (hours < 24) {
                    meta += " • " + hours + " hours ago";
                } else {
                    meta += " • " + (hours / 24) + " days ago";
                }
            }
            textArticleMeta.setText(meta);

            // Set status
            String status = (String) article.get("status");
            if (status != null) {
                textArticleStatus.setText(status.toUpperCase(Locale.getDefault()));
                textArticleStatus.setVisibility(View.VISIBLE);
            } else {
                textArticleStatus.setVisibility(View.GONE);
            }

            // Show badges
            Boolean featured = article.get("featured") != null ? (Boolean) article.get("featured") : false;
            Boolean pinned = article.get("pinned") != null ? (Boolean) article.get("pinned") : false;
            Boolean breaking = article.get("breaking") != null ? (Boolean) article.get("breaking") : false;

            badgeFeatured.setVisibility(featured != null && featured ? View.VISIBLE : View.GONE);
            badgePinned.setVisibility(pinned != null && pinned ? View.VISIBLE : View.GONE);
            badgeBreaking.setVisibility(breaking != null && breaking ? View.VISIBLE : View.GONE);

            // Set stats
            Long viewCount = article.get("viewCount") != null ? ((Number) article.get("viewCount")).longValue() : 0;
            Long likeCount = article.get("likeCount") != null ? ((Number) article.get("likeCount")).longValue() : 0;
            Long commentCount = article.get("commentCount") != null ? ((Number) article.get("commentCount")).longValue() : 0;

            textViews.setText(formatNumber(viewCount) + " views");
            textLikes.setText(formatNumber(likeCount) + " likes");
            textComments.setText(formatNumber(commentCount) + " comments");

            buttonEdit.setOnClickListener(v -> listener.onEdit(article));
            buttonDelete.setOnClickListener(v -> listener.onDelete(article));
        }

        private String formatNumber(long number) {
            if (number >= 1_000_000) {
                return String.format(Locale.getDefault(), "%.1fM", number / 1_000_000.0);
            } else if (number >= 1_000) {
                return String.format(Locale.getDefault(), "%.1fK", number / 1_000.0);
            } else {
                return String.valueOf(number);
            }
        }
    }
}

